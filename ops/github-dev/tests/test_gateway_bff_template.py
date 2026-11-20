"""网关 BFF（第5阶段）的 Nacos 模板契约：Keycloak 登录注册、Bearer 校验、TokenRelay 与去 Cookie、会话与 Swagger UI 的 CSRF。

网关代码（gateway 仓库 com.kellen.config.security）与它的集成测试按这些键工作；模板漏一项，网关会登录不了、令牌转发不了或把会话 Cookie 带进下游。
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from render_nacos import yaml_json

TEMPLATE = Path(__file__).resolve().parents[1] / "nacos" / "DEFAULT_GROUP" / "gateway-spring.yaml"


class GatewayBffTemplateTests(unittest.TestCase):
    def setUp(self):
        self.config = yaml_json(TEMPLATE.read_text())
        self.webflux = self.config["spring"]["cloud"]["gateway"]["server"]["webflux"]

    def test_default_filters_relay_tokens_and_strip_cookies_after_rate_limiting(self):
        names = [item if isinstance(item, str) else item["name"] for item in self.webflux["default-filters"]]
        self.assertEqual(names, ["RequestRateLimiter", "TokenRelay", "RemoveRequestHeader=Cookie"])

    def test_gateway_owns_login_callback_and_logout_paths(self):
        # /oauth2/**、/login/**、/logout 是网关自己的登录入口、Keycloak 回调与注销，不能配成转发路由。
        predicates = [predicate for route in self.webflux["routes"] for predicate in route["predicates"]]
        for predicate in predicates:
            for path in predicate.removeprefix("Path=").split(","):
                self.assertFalse(path.startswith(("/oauth2", "/login", "/logout")), predicate)
                self.assertNotIn(path, ("/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"), predicate)
        user = next(route for route in self.webflux["routes"] if route["id"] == "github-user")
        self.assertEqual(user["predicates"], ["Path=/auth/**"])

    def test_keycloak_login_registration_uses_pkce_client_and_runtime_secret(self):
        oauth2 = self.config["spring"]["security"]["oauth2"]
        registration = oauth2["client"]["registration"]["keycloak"]
        self.assertEqual(registration["provider"], "keycloak")
        self.assertEqual(registration["client-id"], "github-gateway")
        self.assertEqual(registration["client-secret"], "${GITHUB_KEYCLOAK_GATEWAY_CLIENT_SECRET}")
        self.assertEqual(registration["authorization-grant-type"], "authorization_code")
        self.assertEqual(registration["scope"], "openid")
        self.assertEqual(oauth2["client"]["provider"]["keycloak"]["issuer-uri"], "${custom.keycloak-issuer-uri}")

    def test_bearer_requests_are_checked_against_keycloak_for_routed_audiences(self):
        jwt = self.config["spring"]["security"]["oauth2"]["resourceserver"]["jwt"]
        self.assertEqual(jwt["issuer-uri"], "${custom.keycloak-issuer-uri}")
        self.assertEqual(jwt["jws-algorithms"], "RS256")
        self.assertEqual(jwt["audiences"], ["github-api", "github-mcp"])

    def test_sessions_live_in_their_own_redis_namespace(self):
        self.assertEqual(self.config["spring"]["session"]["data"]["redis"]["namespace"], "fleet:gateway:session")

    def test_swagger_ui_sends_the_csrf_header(self):
        self.assertIs(self.config["springdoc"]["swagger-ui"]["csrf"]["enabled"], True)


if __name__ == "__main__":
    unittest.main()
