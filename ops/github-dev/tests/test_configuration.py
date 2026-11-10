"""网关迁移反例：重复前缀、缺少重写和路由优先级不能被单仓编译发现。"""
import sys
import copy
import json
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from verify_configuration import (check_route, route_destination, check_catalog,
                                  check_parameter_comments, effective_configuration)
import verify_configuration as configuration
from render_nacos import yaml_json, render_document


class RouteContractTests(unittest.TestCase):
    def setUp(self):
        self.routes = [
            {"id": "rag", "uri": "lb://rag", "order": 1, "predicates": ["Path=/api/rag/**"]},
            {"id": "ai", "uri": "lb://ai", "order": 2, "predicates": ["Path=/api/**"]},
            {"id": "message", "uri": "lb://message", "predicates": ["Path=/message/**"],
             "filters": ["StripPrefix=1"]},
        ]

    def test_direct_rag_and_prefixed_message_reach_their_controllers(self):
        check_route("/api/rag/documents", "rag", {"/api/rag/documents"}, self.routes)
        check_route("/message/messages/user-messages", "message", {"/messages/user-messages"}, self.routes)

    def test_old_rag_prefix_reproduces_the_migration_404(self):
        with self.assertRaisesRegex(AssertionError, "没有网关路由"):
            check_route("/rag/api/rag/documents", "rag", {"/api/rag/documents"}, self.routes)

    def test_missing_strip_prefix_cannot_reach_message_controller(self):
        self.routes[2]["filters"] = []
        with self.assertRaises(AssertionError):
            check_route("/message/messages/user-messages", "message", {"/messages/user-messages"}, self.routes)

    def test_general_ai_route_cannot_take_priority_over_rag(self):
        self.routes[1]["order"] = 0
        with self.assertRaises(AssertionError):
            check_route("/api/rag/documents", "rag", {"/api/rag/documents"}, self.routes)

    def test_path_segment_boundary_is_not_a_prefix_match(self):
        self.assertEqual(route_destination("/api/ragged/documents", self.routes),
                         ("lb://ai", "/api/ragged/documents"))

    def test_unsupported_rewrites_fail_instead_of_claiming_validation(self):
        self.routes[0]["filters"] = ["RewritePath=/api/rag/(?<segment>.*), /${segment}"]
        with self.assertRaisesRegex(AssertionError, "未支持的路径过滤器"):
            route_destination("/api/rag/documents", self.routes)


class NacosOrganizationTests(unittest.TestCase):
    def test_current_catalog_keeps_shared_model_configuration_for_explicit_rollback_only(self):
        directory = Path(__file__).resolve().parents[1] / "nacos"
        active = {file.name: file.read_text() for file in (directory / "DEFAULT_GROUP").iterdir()}
        catalog = json.loads((directory / "catalog.json").read_text())
        retired = json.loads((directory / "retired-dataids.json").read_text())
        self.assertNotIn("ai-model-secrets.yaml", set(active))
        self.assertNotIn("ai-model-secrets.yaml", {entry["dataId"] for entry in catalog["entries"]})
        self.assertNotIn("ai-model-secrets.yaml", {entry["dataId"] for entry in retired["retired"]})
        historical = yaml_json((directory / "rollback-examples/ai-model-secrets.yaml").read_text())
        self.assertEqual(historical, {"spring": {"ai": {"google": {"genai": {
            "api-key": "${GITHUB_GOOGLE_API_KEY}",
            "chat": {"options": {"model": "${GITHUB_GOOGLE_CHAT_MODEL:gemini-3.1-flash-lite}"}}
        }}}}})
        configuration.check_model_credential_scope(active, catalog, retired)

    def test_agent_model_template_renders_its_google_credentials_and_optional_native_origin(self):
        source = (configuration.CONFIG / "ai-agent-spring.yaml").read_text()
        template = yaml_json(source)
        self.assertIn("google", template["spring"]["ai"])
        google = template["spring"]["ai"]["google"]["genai"]
        self.assertEqual(google["api-key"], "${GITHUB_GOOGLE_API_KEY}")
        self.assertEqual(google["chat"]["options"]["model"], "${GITHUB_GOOGLE_CHAT_MODEL:gemini-3.1-flash-lite}")
        self.assertEqual(template["ai"]["agent"]["google"], {"base-url": "${GITHUB_GOOGLE_BASE_URL:}"})
        environment = {"GITHUB_GOOGLE_API_KEY": "SYNTHETIC_GOOGLE_KEY", "GITHUB_DEEPSEEK_API_KEY": "SYNTHETIC_DEEPSEEK_KEY", "GITHUB_QWEN_API_KEY": "SYNTHETIC_QWEN_KEY"}
        rendered = yaml_json(render_document(source, environment))
        self.assertEqual(rendered["spring"]["ai"]["google"]["genai"]["api-key"], "SYNTHETIC_GOOGLE_KEY")
        self.assertEqual(rendered["spring"]["ai"]["google"]["genai"]["chat"]["options"]["model"], "gemini-3.1-flash-lite")
        self.assertEqual(rendered["ai"]["agent"]["google"]["base-url"], "")
        proxied = yaml_json(render_document(source, {**environment, "GITHUB_GOOGLE_BASE_URL": "http://127.0.0.1:4567/gemini"}))
        self.assertEqual(proxied["ai"]["agent"]["google"]["base-url"], "http://127.0.0.1:4567/gemini")

    def test_shared_model_scope_guard_rejects_reactivation_and_automatic_retirement(self):
        active = {"ai-agent-spring.yaml": (configuration.CONFIG / "ai-agent-spring.yaml").read_text()}
        catalog = {"entries": [{"dataId": "ai-agent-spring.yaml"}]}
        retired = {"retired": []}
        configuration.check_model_credential_scope(active, catalog, retired)
        with self.assertRaisesRegex(AssertionError, "历史共享模型"):
            configuration.check_model_credential_scope({**active, "ai-model-secrets.yaml": "{}"}, catalog, retired)
        altered = copy.deepcopy(catalog)
        altered["entries"].append({"dataId": "ai-model-secrets.yaml"})
        with self.assertRaisesRegex(AssertionError, "历史共享模型"):
            configuration.check_model_credential_scope(active, altered, retired)
        with self.assertRaisesRegex(AssertionError, "不得自动删除"):
            configuration.check_model_credential_scope(active, catalog, {"retired": [{"dataId": "ai-model-secrets.yaml"}]})

    def test_mapping_merge_preserves_later_override_and_ordered_lists(self):
        templates = {"shared.yaml": "spring:\n  enabled: true\n  ports: [1, 2]\n",
                     "service.yaml": "spring:\n  ports: [2, 1]\nservice:\n  enabled: false\n"}
        value = effective_configuration(["nacos:shared.yaml", "optional:nacos:service.yaml?refreshEnabled=true"], templates)
        self.assertEqual(value["spring"], {"enabled": True, "ports": [2, 1]})
        self.assertIs(value["service"]["enabled"], False)
        reversed_value = effective_configuration(["nacos:service.yaml", "nacos:shared.yaml"], templates)
        self.assertNotEqual(value, reversed_value)

    def test_yaml_duplicate_keys_are_not_hidden_by_consolidation(self):
        with self.assertRaises(ValueError):
            effective_configuration(["nacos:a.yaml"], {"a.yaml": "enabled: true\nenabled: false\n"})

    def test_each_leaf_and_list_requires_an_adjacent_comment(self):
        check_parameter_comments("service:\n  # 毫秒超时。\n  timeout: 500\n  # 有序目标。\n  hosts: [a, b]\n")
        with self.assertRaisesRegex(AssertionError, "参数缺少说明"):
            check_parameter_comments("# 文件说明不能代替参数说明。\nservice:\n  timeout: 500\n")
        with self.assertRaisesRegex(AssertionError, "参数缺少说明"):
            check_parameter_comments("service:\n  hosts:\n    # 列表项说明不能代替完整列表语义。\n    - a\n")

    def test_catalog_rejects_missing_consumers_and_reintroduced_retired_files(self):
        templates = {"service.yaml": "# 配置分类：服务配置\n# dataId：service.yaml | group：DEFAULT_GROUP | namespace：github-dev\n# 用途：示例用途。\n# 导入服务：service。\n"}
        catalog = {"namespace": "github-dev", "group": "DEFAULT_GROUP", "entries": [
            {"dataId": "service.yaml", "description": "示例用途；个人配置", "category": "服务配置", "consumers": ["service"]}]}
        retired = {"namespace": "github-dev", "group": "DEFAULT_GROUP", "retired": [
            {"dataId": "old.yaml", "replacement": "service.yaml"}]}
        check_catalog(templates, catalog, retired, {"service.yaml": {"service"}})
        with self.assertRaisesRegex(AssertionError, "消费者说明未同步"):
            check_catalog(templates, catalog, retired, {"service.yaml": {"other"}})
        retired["retired"][0]["dataId"] = "service.yaml"
        with self.assertRaisesRegex(AssertionError, "仍在模板目录"):
            check_catalog(templates, catalog, retired, {"service.yaml": {"service"}})


if __name__ == "__main__":
    unittest.main()
