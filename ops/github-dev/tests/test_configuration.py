"""网关迁移反例：重复前缀、缺少重写和路由优先级不能被单仓编译发现。"""
import sys
import copy
import json
import tempfile
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


class SplitAiRouteContractTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="fleet-route-contract-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.routes = [
            {"id": "user", "uri": "lb://user", "predicates": ["Path=/auth/**"]},
            {"id": "rag", "uri": "lb://rag", "order": 1, "predicates": ["Path=/api/rag/**"]},
            {"id": "ai", "uri": "lb://ai", "order": 2, "predicates": ["Path=/api/**"]},
            {"id": "message", "uri": "lb://message", "predicates": ["Path=/message/**"], "filters": ["StripPrefix=1"]},
        ]
        self.put("admin-web/src/constants/auth.ts", "\n".join(
            "export const " + name + "_SERVICE_PREFIX = '" + value + "';"
            for name, value in (("USER", ""), ("MESSAGE", "/message"), ("AI", ""), ("RAG", ""))))
        for module, service, path in (("auth", "user", "/auth/manage/users"), ("message", "message", "/messages/user-messages"),
                                      ("ai", "ai", "/api/ai/model-selection"), ("rag", "rag", "/api/rag/documents")):
            self.put(f"admin-web/src/services/{module}/index.ts", f"request('{path}');")
            self.put(f"{service}/src/main/java/Legacy.java", f'@RestController @RequestMapping("{path}") class Legacy {{ @GetMapping Object current(){{return null;}} }}')
        self.storage = self.root / "admin-web/src/services/ai/storage.ts"
        for name in ("controlPlane.ts", "storage.ts"):
            self.put("admin-web/src/services/ai/" + name, (configuration.ROOT / "admin-web/src/services/ai" / name).read_text())
        for package, name in (("modelcontrolplane", "ModelControlPlaneController.java"), ("storageconfig", "StorageConfigurationController.java")):
            self.put("ai/src/main/java/" + name, (configuration.ROOT / "ai/src/main/java/com/kellen" / package / name).read_text())

    def put(self, relative, value):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)

    def check(self):
        return configuration.check_frontend_routes(self.root, self.routes)

    def test_real_split_modules_add_seven_unique_paths_to_the_four_legacy_fixture_paths(self):
        self.assertEqual(self.check(), 11)

    def test_wrong_storage_root_is_not_silently_ignored(self):
        self.storage.write_text(self.storage.read_text().replace("${AI_SERVICE_PREFIX}/api/ai", "${AI_SERVICE_PREFIX}/api/rag"))
        with self.assertRaises(AssertionError): self.check()

    def test_wrong_control_plane_template_does_not_escape_the_ai_route_scope(self):
        control = self.root / "admin-web/src/services/ai/controlPlane.ts"
        control.write_text(control.read_text().replace("${AI_SERVICE_PREFIX}/api/ai/model-control-plane", "${AI_SERVICE_PREFIX}/api/rag/model-control-plane"))
        with self.assertRaises(AssertionError): self.check()

    def test_comments_and_string_messages_are_not_additional_network_calls(self):
        self.storage.write_text(self.storage.read_text() + "\n// export const ignored = () => storageRequest('/not-a-route', {});\nconst note = \"request('/not-a-route')\";\n")
        self.assertEqual(self.check(), 11)

    def test_existing_same_root_media_type_annotations_do_not_hide_valid_paths(self):
        self.put("ai/src/main/java/Dialogue.java", '@RestController @RequestMapping("/api/ai") class Dialogue { @PostMapping(value="/chat/stream", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.TEXT_EVENT_STREAM_VALUE) Object stream(){return null;} }')
        self.assertEqual(self.check(), 11)

    def test_unknown_controller_path_expression_is_not_guessed(self):
        self.put("ai/src/main/java/Unknown.java", '@RestController @RequestMapping("/api/ai") class Unknown { @GetMapping(UNKNOWN_ROUTE) Object current(){return null;} }')
        with self.assertRaisesRegex(AssertionError, "控制器路径"): self.check()

    def test_wrong_ai_prefix_rejects_split_control_plane_and_storage(self):
        constants = self.root / "admin-web/src/constants/auth.ts"
        constants.write_text(constants.read_text().replace("AI_SERVICE_PREFIX = ''", "AI_SERVICE_PREFIX = '/ai'"))
        with self.assertRaises(AssertionError): self.check()

    def test_unparsed_relative_path_and_dynamic_encoding_fail_closed(self):
        source = self.storage.read_text()
        for changed in (source.replace("'/storage-settings'", "resolveStoragePath()"),
                        source.replace("encodeURIComponent(id)", "encodeURIComponent(id + suffix)"),
                        source.replace("${root}${path}", "${root}${otherPath}")):
            self.storage.write_text(changed)
            with self.assertRaises(AssertionError): self.check()

    def test_new_path_is_extracted_and_checked_against_actual_controller_annotations(self):
        self.storage.write_text(self.storage.read_text() + "\nexport const added = () => storageRequest<unknown>('/storage-new', {});\n")
        with self.assertRaises(AssertionError): self.check()
        controller = self.root / "ai/src/main/java/StorageConfigurationController.java"
        source = controller.read_text()
        before, _, after = source.rpartition("}")
        controller.write_text(before + '@GetMapping("/storage-new") public Object added(){return null;}\n}' + after)
        self.assertEqual(self.check(), 12)

    def test_unknown_network_expression_does_not_report_partial_coverage(self):
        self.storage.write_text(self.storage.read_text() + "\nexport const bypass = () => fetch('/api/ai/storage-new');\n")
        with self.assertRaises(AssertionError): self.check()


class NacosOrganizationTests(unittest.TestCase):
    def test_new_database_structure_contains_storage_control_plane_without_data_mutations(self):
        documents, manifest = configuration.prepare(configuration.ROOT)
        self.assertTrue({"ai_storage_configuration", "ai_storage_setting", "ai_storage_operation"} <= set(manifest["ai"]["tables"]))
        self.assertNotIn("INSERT INTO ai_storage_setting", documents["github_dev_ai"])

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

    def test_storage_connections_are_console_owned_and_rollback_is_not_automatically_retired(self):
        directory = configuration.CONFIG.parent
        active = {file.name: file.read_text() for file in configuration.CONFIG.iterdir()}
        catalog = json.loads((directory / "catalog.json").read_text())
        retired = json.loads((directory / "retired-dataids.json").read_text())
        self.assertNotIn("storage.yaml", set(active))
        self.assertNotIn("storage.yaml", {entry["dataId"] for entry in catalog["entries"]})
        self.assertNotIn("storage.yaml", {entry["dataId"] for entry in retired["retired"]})
        self.assertNotIn("oss", yaml_json(active["ai-spring.yaml"])["ai"])
        self.assertEqual(yaml_json((directory / "rollback-examples/storage.yaml").read_text())["dromara"]["x-file-storage"]["default-platform"], "minio-private")
        configuration.check_storage_credential_scope(active, catalog, retired)
        for changed, changed_catalog, changed_retired in (
            ({**active, "storage.yaml": "{}"}, catalog, retired),
            (active, {"entries": [*catalog["entries"], {"dataId": "storage.yaml"}]}, retired),
            (active, catalog, {"retired": [*retired["retired"], {"dataId": "storage.yaml"}]}),
            ({**active, "ai-spring.yaml": "ai:\n  oss:\n    base-dir: old\n"}, catalog, retired),
        ):
            with self.assertRaises(AssertionError):
                configuration.check_storage_credential_scope(changed, changed_catalog, changed_retired)

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
