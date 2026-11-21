"""Nacos 开启客户端鉴权后两类账号分开：服务只拿服务账号，运维脚本只用管理员账号。"""
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
import start_service  # noqa: E402

VALUES = {"GITHUB_NACOS_SERVER_ADDR": "http://nacos.test:8848",
          "GITHUB_NACOS_USERNAME": "unit-app", "GITHUB_NACOS_PASSWORD": "unit-app-password",
          "GITHUB_NACOS_ADMIN_USERNAME": "unit-admin", "GITHUB_NACOS_ADMIN_PASSWORD": "unit-admin-password"}


class NacosAdminCredentialsTest(unittest.TestCase):
    def test_service_process_never_receives_nacos_admin_credentials(self):
        inherited = {"PATH": "/usr/bin", "GITHUB_NACOS_ADMIN_PASSWORD": "inherited-admin-password"}
        environment = start_service.service_environment(inherited, VALUES)
        self.assertNotIn("GITHUB_NACOS_ADMIN_USERNAME", environment)
        self.assertNotIn("GITHUB_NACOS_ADMIN_PASSWORD", environment)
        self.assertEqual(environment["GITHUB_NACOS_USERNAME"], "unit-app")
        self.assertEqual(environment["GITHUB_NACOS_PASSWORD"], "unit-app-password")
        self.assertEqual((environment["APP_ENV"], environment["GITHUB_NACOS_DEV_NAMESPACE"]), ("dev", "github-dev"))
        self.assertEqual(environment["PATH"], "/usr/bin")

    def test_config_sync_publishes_with_admin_account(self):
        source = (SCRIPTS / "GithubNacosConfigSync.java").read_text()
        self.assertIn('"GITHUB_NACOS_ADMIN_"', source)
        self.assertNotIn('System.getenv("GITHUB_NACOS_" + key', source)


if __name__ == "__main__":
    unittest.main()
