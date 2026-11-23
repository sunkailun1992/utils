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


class FrontendRouteContractTests(unittest.TestCase):
    # 有限语法的合成夹具，照 admin-web 1.2.5 起的多应用结构（apps/<应用>/src/services 与 packages/auth），
    # 不在单仓 CI 中读取相邻仓库；真实跨仓源码由完整 fleet 上的 verify_configuration.py 独立核对。
    FILES = {
        "admin-web/apps/system/src/services/api.ts": """
import { createServiceClient } from '@admin-web/api';
import { client } from '../http.ts';
export const api = createServiceClient(client);
""",
        "admin-web/apps/system/src/services/user.ts": """
import { api } from './api.ts';
export const queryUsers = (query: unknown) => api.getPage<unknown>('/auth/manage/users', query);
export const removeUser = ({ id }: { id: string }) => api.remove<boolean>(`/auth/manage/users/${id}`, {});
""",
        "admin-web/apps/message/src/services/message.ts": """
import { createServiceClient } from '@admin-web/api';
import { client } from '../http.ts';
const PREFIX = '/message';
const api = createServiceClient(client);
export const queryUserMessages = (query: unknown) =>
  api.getPage<unknown>(`${PREFIX}/messages/user-messages`, query);
export async function countCurrentUnreadMessages(query: unknown) {
  const response = await client.get<ApiResponse<number | null>>(
    `${PREFIX}/messages/user-messages/current/unread-count`,
    { params: query, skipErrorHandler: true },
  );
  return response.data;
}
export const listReceivers = (tenantId: string) =>
  api.getList<{ id: string }>('/auth/manage/users/options', { tenantId });
""",
        "admin-web/apps/ai/src/services/controlPlane.ts": """
import { client } from '../http.ts';
export async function getModelControlPlane() {
  const response = await client.get<unknown>('/api/ai/model-control-plane', {});
  return response.data;
}
""",
        "admin-web/apps/ai/src/services/model.ts": """
import { client } from '../http.ts';
const MODEL_SELECTION_URL = '/api/ai/model-selection';
export async function getModelSelection() {
  return (await client.get<unknown>(MODEL_SELECTION_URL, {})).data;
}
export async function updateModelSelection(body: unknown) {
  return (await client.put<unknown>(MODEL_SELECTION_URL, body, {})).data;
}
""",
        "admin-web/apps/ai/src/services/storage.ts": """
import { client } from '../http.ts';
const ROOT = '/api/ai';
const INVALID = 'INVALID_STORAGE_RESPONSE';
async function storage(config: AxiosRequestConfig & { url: string }, options: RequestOptions): Promise<unknown> {
  const response = await client.request<unknown>({ ...config, url: `${ROOT}${config.url}`, signal: options.signal });
  return strictData(response.data, INVALID);
}
const segment = (value: string) => encodeURIComponent(value);
export const listStorageConfigurations = (options: RequestOptions = {}) =>
  storage({ method: 'GET', url: '/storage-configurations' }, options);
export const getStorageSettings = (options: RequestOptions = {}) =>
  storage({ method: 'GET', url: '/storage-settings' }, options);
export const updateStorageConfiguration = (id: string, options: RequestOptions = {}) =>
  storage({ method: 'PUT', url: `/storage-configurations/${segment(id)}` }, options);
export const probeStorageConfiguration = (id: string, options: RequestOptions = {}) =>
  storage({ method: 'POST', url: `/storage-configurations/${segment(id)}/probes` }, options);
""",
        "admin-web/apps/rag/src/services/rag.ts": """
import { client } from '../http.ts';
const segment = (value: string) => encodeURIComponent(value);
async function rag<T>(config: AxiosRequestConfig & { url: string }) {
  return (await client.request<ApiResponse<T>>(config)).data;
}
export const listRagDocuments = () => rag<unknown>({ method: 'GET', url: '/api/rag/documents' });
export const removeRagDocument = (documentId: string) =>
  rag<unknown>({ method: 'DELETE', url: `/api/rag/documents/${segment(documentId)}` });
""",
        "admin-web/packages/auth/src/endpoints.ts": """
export async function fetchCurrentResources(client: AxiosInstance) {
  const response = await client.get<ApiResponse<AuthCurrentResourceVO>>(
    '/auth/current/resources',
    {},
  );
  return response.data;
}
export function logout(client: AxiosInstance) {
  return client.post('/logout', undefined, { skipErrorHandler: true });
}
""",
    }
    CONTROLLERS = {
        "user/src/main/java/AuthUserController.java": """
@RestController @RequestMapping("/auth/manage/users")
class AuthUserController {
  @GetMapping Object page() { return null; }
  @DeleteMapping("/{id}") Object remove() { return null; }
  @GetMapping("/options") Object options() { return null; }
}
""",
        "user/src/main/java/AuthCurrentController.java": """
@RestController @RequestMapping("/auth/current")
class AuthCurrentController { @GetMapping("/resources") Object resources() { return null; } }
""",
        "message/src/main/java/UserMessageController.java": """
@RestController @RequestMapping("/messages/user-messages")
class UserMessageController {
  @GetMapping Object page() { return null; }
  @GetMapping("/current/unread-count") Object unread() { return null; }
}
""",
        "ai/src/main/java/ModelControlPlaneController.java": """
@RestController @RequestMapping("/api/ai/model-control-plane")
class ModelControlPlaneController { @GetMapping Object current() { return null; } }
""",
        "ai/src/main/java/AiModelSelectionController.java": """
@RestController @RequestMapping("/api/ai/model-selection")
class AiModelSelectionController { @GetMapping Object current() { return null; } @PutMapping Object update() { return null; } }
""",
        "ai/src/main/java/StorageConfigurationController.java": """
@RestController @RequestMapping("/api/ai")
class StorageConfigurationController {
  @GetMapping("/storage-configurations") Object list() { return null; }
  @PutMapping("/storage-configurations/{id}") Object update() { return null; }
  @GetMapping("/storage-settings") Object settings() { return null; }
  @PostMapping("/storage-configurations/{id}/probes") Object probe() { return null; }
}
""",
        "rag/src/main/java/RagDocumentController.java": """
@RestController @RequestMapping("/api/rag/documents")
class RagDocumentController { @GetMapping Object list() { return null; } @DeleteMapping("/{documentId}") Object remove() { return null; } }
""",
    }

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
        for relative, source in {**self.FILES, **self.CONTROLLERS}.items():
            self.put(relative, source)
        self.storage = self.root / "admin-web/apps/ai/src/services/storage.ts"

    def put(self, relative, value):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)

    def edit(self, relative, old, new):
        path = self.root / relative
        source = path.read_text()
        self.assertIn(old, source)
        path.write_text(source.replace(old, new))

    def check(self):
        return configuration.check_frontend_routes(self.root, self.routes)

    def test_every_fixture_request_is_checked_and_the_gateway_logout_is_local(self):
        self.assertEqual(self.check(), 14)

    def test_wrong_storage_root_is_not_silently_ignored(self):
        self.edit("admin-web/apps/ai/src/services/storage.ts", "const ROOT = '/api/ai';", "const ROOT = '/api/rag';")
        with self.assertRaises(AssertionError): self.check()

    def test_wrong_control_plane_path_does_not_escape_the_ai_route_scope(self):
        self.edit("admin-web/apps/ai/src/services/controlPlane.ts", "'/api/ai/model-control-plane'", "'/api/rag/model-control-plane'")
        with self.assertRaises(AssertionError): self.check()

    def test_wrong_path_constant_is_checked_like_a_literal(self):
        self.edit("admin-web/apps/ai/src/services/model.ts", "'/api/ai/model-selection'", "'/api/ai/model-selections'")
        with self.assertRaisesRegex(AssertionError, "控制器注解"): self.check()

    def test_message_prefix_comes_from_the_constant_and_must_match_the_gateway(self):
        self.edit("admin-web/apps/message/src/services/message.ts", "const PREFIX = '/message';", "const PREFIX = '/msg';")
        with self.assertRaises(AssertionError): self.check()

    def test_session_paths_are_checked_while_only_the_gateway_logout_stays_local(self):
        self.edit("admin-web/packages/auth/src/endpoints.ts", "'/auth/current/resources'", "'/auth/current/resource'")
        with self.assertRaisesRegex(AssertionError, "控制器注解"): self.check()

    def test_comments_and_string_messages_are_not_additional_network_calls(self):
        self.storage.write_text(self.storage.read_text() + "\n// export const ignored = () => storage({ url: '/not-a-route' }, {});\nconst note = \"client.get('/not-a-route')\";\n")
        self.assertEqual(self.check(), 14)

    def test_existing_same_root_media_type_annotations_do_not_hide_valid_paths(self):
        self.put("ai/src/main/java/Dialogue.java", '@RestController @RequestMapping("/api/ai") class Dialogue { @PostMapping(value="/chat/stream", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.TEXT_EVENT_STREAM_VALUE) Object chat(){return null;} }')
        self.assertEqual(self.check(), 14)

    def test_unknown_controller_path_expression_is_not_guessed(self):
        self.put("ai/src/main/java/Unknown.java", '@RestController @RequestMapping("/api/ai") class Unknown { @GetMapping(UNKNOWN_ROUTE) Object current(){return null;} }')
        with self.assertRaisesRegex(AssertionError, "控制器路径"): self.check()

    def test_request_parameter_and_header_conditions_do_not_change_the_path(self):
        # user 的管理接口在同一路径上按 params 区分分页与列表；params、headers 只是请求条件，不改变路径。
        self.put("user/src/main/java/AuthUserController.java", """
@RestController @RequestMapping("/auth/manage/users")
class AuthUserController {
  @GetMapping(params = {"current", "size"}) Object page() { return null; }
  @GetMapping(value = "/options", headers = "X-Tenant-Id") Object options() { return null; }
  @DeleteMapping("/{id}") Object remove() { return null; }
}
""")
        self.assertEqual(self.check(), 14)
        self.edit("user/src/main/java/AuthUserController.java", 'params = {"current", "size"}', "params = PAGE_PARAMS")
        with self.assertRaisesRegex(AssertionError, "控制器"): self.check()

    def test_unparsed_paths_and_dynamic_parameters_fail_closed(self):
        for relative, old, new in (
                ("admin-web/apps/ai/src/services/storage.ts", "url: '/storage-settings'", "url: resolveStoragePath()"),
                ("admin-web/apps/ai/src/services/storage.ts", "segment(id)}/probes", "segment(id + suffix)}/probes"),
                ("admin-web/apps/ai/src/services/storage.ts", "${ROOT}${config.url}", "${ROOT}${config.path}"),
                ("admin-web/apps/message/src/services/message.ts", "`${PREFIX}/messages/user-messages`", "`${prefix()}/messages/user-messages`"),
                ("admin-web/apps/system/src/services/user.ts", "'/auth/manage/users', query", "pathFromCaller, query"),
                ("admin-web/apps/rag/src/services/rag.ts", "(await client.request<ApiResponse<T>>(config))", "(await client.request<ApiResponse<T>>(otherConfig))")):
            with self.subTest(new=new):
                self.setUp()
                self.edit(relative, old, new)
                with self.assertRaises(AssertionError): self.check()

    def test_unknown_request_method_or_network_expression_fails_closed(self):
        for relative, addition in (
                ("admin-web/apps/system/src/services/user.ts", "\nexport const download = () => api.download<Blob>('/auth/manage/users/export', {});\n"),
                ("admin-web/apps/ai/src/services/storage.ts", "\nexport const bypass = () => fetch('/api/ai/storage-new');\n")):
            with self.subTest(addition=addition):
                self.setUp()
                path = self.root / relative
                path.write_text(path.read_text() + addition)
                with self.assertRaises(AssertionError): self.check()

    def test_new_path_is_extracted_and_checked_against_controller_annotations(self):
        self.storage.write_text(self.storage.read_text() + "\nexport const added = () => storage({ method: 'GET', url: '/storage-new' }, {});\n")
        with self.assertRaisesRegex(AssertionError, "控制器注解"): self.check()
        self.edit("ai/src/main/java/StorageConfigurationController.java", '@GetMapping("/storage-settings")',
                  '@GetMapping("/storage-new") Object added() { return null; }\n  @GetMapping("/storage-settings")')
        self.assertEqual(self.check(), 15)

    def test_a_frontend_app_without_requests_is_a_layout_change_not_a_pass(self):
        (self.root / "admin-web/apps/rag/src/services/rag.ts").unlink()
        with self.assertRaisesRegex(AssertionError, "apps/rag"): self.check()


class NacosOrganizationTests(unittest.TestCase):
    def test_shared_model_configuration_is_retired_and_cannot_return(self):
        directory = Path(__file__).resolve().parents[1] / "nacos"
        active = {file.name: file.read_text() for file in (directory / "DEFAULT_GROUP").iterdir()}
        catalog = json.loads((directory / "catalog.json").read_text())
        retired = json.loads((directory / "retired-dataids.json").read_text())
        self.assertNotIn("ai-model-secrets.yaml", set(active))
        self.assertNotIn("ai-model-secrets.yaml", {entry["dataId"] for entry in catalog["entries"]})
        self.assertIn({"dataId": "ai-model-secrets.yaml", "replacement": None},
                      [{"dataId": entry["dataId"], "replacement": entry["replacement"]} for entry in retired["retired"]])
        self.assertFalse((directory / "rollback-examples").exists())
        configuration.check_model_credential_scope(active, catalog, retired)

    def test_storage_connections_are_console_owned_and_old_storage_is_retired(self):
        directory = configuration.CONFIG.parent
        active = {file.name: file.read_text() for file in configuration.CONFIG.iterdir()}
        catalog = json.loads((directory / "catalog.json").read_text())
        retired = json.loads((directory / "retired-dataids.json").read_text())
        self.assertNotIn("storage.yaml", set(active))
        self.assertNotIn("storage.yaml", {entry["dataId"] for entry in catalog["entries"]})
        self.assertIn("storage.yaml", {entry["dataId"] for entry in retired["retired"]})
        self.assertNotIn("oss", yaml_json(active["ai-spring.yaml"])["ai"])
        configuration.check_storage_credential_scope(active, catalog, retired)
        without_storage = {"retired": [entry for entry in retired["retired"] if entry["dataId"] != "storage.yaml"]}
        for changed, changed_catalog, changed_retired in (
            ({**active, "storage.yaml": "{}"}, catalog, retired),
            (active, {"entries": [*catalog["entries"], {"dataId": "storage.yaml"}]}, retired),
            (active, catalog, without_storage),
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
        environment = {"GITHUB_GOOGLE_API_KEY": "SYNTHETIC_GOOGLE_KEY", "GITHUB_DEEPSEEK_API_KEY": "SYNTHETIC_DEEPSEEK_KEY", "GITHUB_QWEN_API_KEY": "SYNTHETIC_QWEN_KEY",
                       "GITHUB_POSTGRESQL_AI_AGENT_USERNAME": "synthetic_agent_user", "GITHUB_POSTGRESQL_AI_AGENT_PASSWORD": "SYNTHETIC_AGENT_DB"}
        rendered = yaml_json(render_document(source, environment))
        # 第5阶段起 ai-agent 与其他服务一样有自己的库（只放 Seata AT 的 undo_log），灰度池复用主库。
        master = rendered["spring"]["datasource"]["dynamic"]["datasource"]["master"]
        self.assertTrue(master["url"].endswith("/github_dev_ai_agent?tcpKeepAlive=true"))
        self.assertEqual((master["username"], master["password"]), ("synthetic_agent_user", "SYNTHETIC_AGENT_DB"))
        self.assertEqual(rendered["spring"]["datasource"]["dynamic"]["datasource"]["gray"]["url"], "${spring.datasource.dynamic.datasource.master.url}")
        self.assertEqual(rendered["spring"]["ai"]["google"]["genai"]["api-key"], "SYNTHETIC_GOOGLE_KEY")
        self.assertEqual(rendered["spring"]["ai"]["google"]["genai"]["chat"]["options"]["model"], "gemini-3.1-flash-lite")
        self.assertEqual(rendered["ai"]["agent"]["google"]["base-url"], "")
        proxied = yaml_json(render_document(source, {**environment, "GITHUB_GOOGLE_BASE_URL": "http://127.0.0.1:4567/gemini"}))
        self.assertEqual(proxied["ai"]["agent"]["google"]["base-url"], "http://127.0.0.1:4567/gemini")

    def test_shared_model_scope_guard_rejects_reactivation_and_missing_retirement(self):
        active = {"ai-agent-spring.yaml": (configuration.CONFIG / "ai-agent-spring.yaml").read_text()}
        catalog = {"entries": [{"dataId": "ai-agent-spring.yaml"}]}
        retired = {"retired": [{"dataId": "ai-model-secrets.yaml", "replacement": None}]}
        configuration.check_model_credential_scope(active, catalog, retired)
        with self.assertRaisesRegex(AssertionError, "历史共享模型"):
            configuration.check_model_credential_scope({**active, "ai-model-secrets.yaml": "{}"}, catalog, retired)
        altered = copy.deepcopy(catalog)
        altered["entries"].append({"dataId": "ai-model-secrets.yaml"})
        with self.assertRaisesRegex(AssertionError, "历史共享模型"):
            configuration.check_model_credential_scope(active, altered, retired)
        with self.assertRaisesRegex(AssertionError, "退役项"):
            configuration.check_model_credential_scope(active, catalog, {"retired": []})
        with self.assertRaisesRegex(AssertionError, "退役项"):
            configuration.check_model_credential_scope(active, catalog, {"retired": [
                {"dataId": "ai-model-secrets.yaml", "replacement": "ai-agent-spring.yaml"}]})

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


class NacosCredentialTests(unittest.TestCase):
    """服务连 Nacos 的账号只从私有环境来；缺值必须启动失败，不能回落到默认账号（默认值 nacos 就是管理员）。"""

    @staticmethod
    def profile(username, password):
        return {"custom": {"nacos-username": username, "nacos-password": password},
                "spring": {"cloud": {"nacos": {kind: {"username": "${custom.nacos-username}",
                                                      "password": "${custom.nacos-password}"}
                                               for kind in ("config", "discovery")}}}}

    def test_credentials_that_only_reference_the_private_environment_pass(self):
        configuration.check_nacos_credentials(
            self.profile("${GITHUB_NACOS_USERNAME}", "${GITHUB_NACOS_PASSWORD}"), "svc/application-dev.yml")

    def test_a_default_account_fallback_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, r"custom\.nacos-username"):
            configuration.check_nacos_credentials(
                self.profile("${GITHUB_NACOS_USERNAME:nacos}", "${GITHUB_NACOS_PASSWORD}"), "svc/application-dev.yml")


class ResourceServerAudienceTests(unittest.TestCase):
    """配了签发方的服务模板必须写明受众；utils 2.0.6 起漏配时回落到 github-api，但模板仍要显式写出，避免依赖缺省值。"""

    def test_templates_with_an_issuer_declare_their_audiences(self):
        templates = {file.name: file.read_text() for file in configuration.CONFIG.iterdir()}
        configuration.check_resource_server_audiences(templates)

    def test_an_issuer_without_audiences_is_reported_by_data_id(self):
        templates = {
            "ok-spring.yaml": "spring:\n  security:\n    oauth2:\n      resourceserver:\n        jwt:\n"
                              "          issuer-uri: x\n          audiences: github-api\n",
            "missing-spring.yaml": "spring:\n  security:\n    oauth2:\n      resourceserver:\n        jwt:\n"
                                   "          issuer-uri: x\n",
            "plain.yaml": "a: 1\n",
        }
        with self.assertRaisesRegex(AssertionError, r"missing-spring\.yaml"):
            configuration.check_resource_server_audiences(templates)


class RetiredKeyTests(unittest.TestCase):
    """第5阶段退役的键（自签令牌、共享密钥、服务签名与旧 OAuth 设置）不得留在模板里。"""

    def test_phase5_retired_keys_are_absent_from_the_templates(self):
        templates = {file.name: file.read_text() for file in configuration.CONFIG.iterdir()}
        configuration.check_retired_keys(templates)

    def test_a_retired_key_left_in_a_template_is_reported_by_path(self):
        templates = {name: "kept: 1\n" for name in configuration.RETIRED_KEYS}
        templates["rag-spring.yaml"] = "rag:\n  engine:\n    base-url: x\n    api-key: y\n"
        with self.assertRaisesRegex(AssertionError, r"rag-spring\.yaml:rag\.engine\.api-key"):
            configuration.check_retired_keys(templates)


if __name__ == "__main__":
    unittest.main()
