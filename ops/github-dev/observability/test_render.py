"""运行渲染器验证采集完整性和输入边界；不连接真实服务。"""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("fleet_render", Path(__file__).with_name("render.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RenderTest(unittest.TestCase):
    TOKEN_URL = "http://keycloak.test:8080/realms/github-dev/protocol/openid-connect/token"

    def fixture(self):
        return {"environment": "github-dev", "services": {
            name: f"127.0.0.1:{18000 + index}"
            for index, name in enumerate(MODULE.SERVICES)
        }, "engine": "127.0.0.1:18091", "keycloak_token_url": self.TOKEN_URL}

    def test_complete_inventory_preserves_application_and_auth(self):
        result = MODULE.render(self.fixture())
        jobs = result["scrape_configs"]
        fleet = [job for job in jobs if job["job_name"].startswith("fleet-")]
        self.assertEqual(len(fleet), 10)
        gateway = next(job for job in fleet if job["job_name"] == "fleet-gateway")
        self.assertEqual(gateway["static_configs"][0]["labels"]["application"], "gateway")
        self.assertEqual(gateway["http_headers"]["actuatorReq"]["files"], ["/run/secrets/actuator_token"])
        engine = next(job for job in fleet if job["job_name"] == "fleet-rag-engine")
        self.assertEqual(engine["metrics_path"], "/metrics")

    def test_engine_job_uses_oauth2_client_credentials(self):
        """Engine只收Keycloak签给github-prometheus的令牌：Prometheus内置oauth2按客户端凭证取令牌，口令只引用私有文件。"""
        jobs = MODULE.render(self.fixture())["scrape_configs"]
        engine = next(job for job in jobs if job["job_name"] == "fleet-rag-engine")
        self.assertNotIn("authorization", engine)
        self.assertEqual(engine["oauth2"], {
            "client_id": "github-prometheus",
            "client_secret_file": "/run/secrets/prometheus_client_secret",
            "token_url": self.TOKEN_URL,
        })
        java = [job for job in jobs if job["job_name"].startswith("fleet-") and job is not engine]
        self.assertTrue(all("oauth2" not in job for job in java))

    def test_token_url_must_be_plain_http_url_without_credentials(self):
        for url in (
            "http://KEYCLOAK_PRIVATE_HOST:8080/realms/github-dev/protocol/openid-connect/token",
            "http://user:secret@keycloak.test/realms/github-dev/protocol/openid-connect/token",
            "http://keycloak.test/realms/github-dev/protocol/openid-connect/token?client_secret=x",
            "http://keycloak.test/realms/github-dev/protocol/openid-connect/token#x",
            "ftp://keycloak.test/realms/github-dev/protocol/openid-connect/token",
            "keycloak.test:8080/realms/github-dev/protocol/openid-connect/token",
            "http://keycloak.test:8080/realms/github-dev",
            None,
        ):
            fixture = self.fixture()
            fixture["keycloak_token_url"] = url
            with self.subTest(url=url), self.assertRaises((ValueError, TypeError)):
                MODULE.render(fixture)

    def test_missing_service_is_an_error_instead_of_silent_target_absence(self):
        fixture = self.fixture()
        del fixture["services"]["mcp"]
        with self.assertRaises(ValueError):
            MODULE.render(fixture)

    def test_target_cannot_contain_credentials_url_path_or_query(self):
        for target in ["http://host:7200", "user:pass@host:7200", "host:7200/path", "host:7200?key=secret", "host:0", "host:65536", "JAVA_PRIVATE_HOST:7200"]:
            fixture = self.fixture()
            fixture["services"]["rag"] = target
            with self.subTest(target=target), self.assertRaises(ValueError):
                MODULE.render(fixture)

    def test_ipv6_and_hostname_are_supported(self):
        fixture = self.fixture()
        fixture["services"]["gateway"] = "[::1]:17100"
        fixture["services"]["mcp"] = "mcp.internal:7700"
        self.assertEqual(len(MODULE.render(fixture)["scrape_configs"]), 12)


if __name__ == "__main__":
    unittest.main()
