"""个人 PG 模板与结构初始化合同；不连接数据库，也不创建人员或权限数据。"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from render_nacos import yaml_json
from verify_configuration import check_parameter_comments

ROOT = Path(__file__).resolve().parents[4]
CONFIG = ROOT / "utils/ops/github-dev/nacos/DEFAULT_GROUP"
SERVICES = ("ai", "ai-memory", "mcp", "message", "rag", "report", "user")


class PostgresqlConfigurationTests(unittest.TestCase):
    def test_each_service_has_its_own_postgresql_role_and_no_active_mysql(self):
        """主库用本服务独立的 PostgreSQL 账号；不再有 MySQL 数据源，也不再有第3阶段删除的 postgresql 旧键。"""
        for service in SERVICES:
            with self.subTest(service=service):
                source = (CONFIG / f"{service}-spring.yaml").read_text()
                value = yaml_json(source)
                for retired in ("mysql", "mysql-gray", "postgresql", "postgresql-gray"):
                    self.assertNotIn(retired, value)
                master = value["spring"]["datasource"]["dynamic"]["datasource"]["master"]
                suffix = service.replace("-", "_")
                prefix = "GITHUB_POSTGRESQL_" + suffix.upper()
                self.assertTrue(master["url"].startswith("jdbc:postgresql://"))
                self.assertIn(f"/github_dev_{suffix}?", master["url"])
                self.assertEqual(master["username"], "${" + prefix + "_USERNAME}")
                self.assertEqual(master["password"], "${" + prefix + "_PASSWORD}")
                self.assertEqual(master["driver-class-name"], "org.postgresql.Driver")
                check_parameter_comments(source)

    def test_dynamic_datasource_keys_have_no_timeout_options(self):
        """dynamic-datasource 原生键：URL 不带 Druid 会误读的超时参数，灰度库逐项复用主库。"""
        for service in SERVICES:
            with self.subTest(service=service):
                source = (CONFIG / f"{service}-spring.yaml").read_text()
                pools = yaml_json(source)["spring"]["datasource"]["dynamic"]["datasource"]
                self.assertEqual(set(pools), {"master", "gray"})
                master = pools["master"]
                suffix = service.replace("-", "_")
                self.assertEqual(master["url"], "jdbc:postgresql://${custom.infra-postgresql-host}:"
                                 "${custom.infra-postgresql-port}/github_dev_" + suffix + "?tcpKeepAlive=true")
                self.assertNotRegex(master["url"], r"(?i)(connect|socket)Timeout")
                self.assertEqual(pools["gray"], {key: "${spring.datasource.dynamic.datasource.master." + key + "}"
                                                 for key in master})
        seata = yaml_json((CONFIG / "seata.yaml").read_text())["seata"]
        self.assertIs(seata["enable-auto-data-source-proxy"], False)

    def test_seata_is_always_on_and_points_every_service_at_the_shared_tc(self):
        """Seata 不设总开关：数据源块固定 seata: true，客户端按 file 注册直连 NAS 上的 TC（原生协议 7091）。"""
        source = (CONFIG / "seata.yaml").read_text()
        seata = yaml_json(source)["seata"]
        self.assertNotIn("enabled", seata)
        self.assertIs(seata["enable-auto-data-source-proxy"], False)
        group = seata["tx-service-group"]
        self.assertEqual(seata["service"]["vgroup-mapping"], {group: "default"})
        self.assertEqual(seata["service"]["grouplist"],
                         {"default": "${custom.infra-seata-host}:${custom.infra-seata-port}"})
        self.assertEqual(seata["registry"], {"type": "file"})
        self.assertEqual(seata["config"], {"type": "file"})
        shared = yaml_json((CONFIG / "reuse-configuration.yaml").read_text())["custom"]
        self.assertEqual(shared["infra-seata-host"], "${GITHUB_SEATA_HOST}")
        self.assertEqual(shared["infra-seata-port"], "${GITHUB_SEATA_PORT:7091}")
        check_parameter_comments(source)

    def test_data_permission_uses_utils_default_columns(self):
        """每行记创建部门：没写表规则列时用 utils 默认的 creator、create_dept，Nacos 不再覆盖成旧列名。"""
        source = (CONFIG / "security-auth.yaml").read_text()
        permission = yaml_json(source)["security"]["data-permission"]
        self.assertNotIn("default-user-column", permission)
        self.assertNotIn("default-dept-column", permission)
        self.assertEqual(set(permission["table-rules"]), {"auth_user", "auth_dept", "auth_role"})
        check_parameter_comments(source)

    def test_shared_connection_and_boolean_sql_contract_are_postgresql(self):
        shared = yaml_json((CONFIG / "reuse-configuration.yaml").read_text())["custom"]
        self.assertEqual(shared["infra-postgresql-host"], "${GITHUB_POSTGRESQL_HOST}")
        self.assertEqual(shared["infra-postgresql-port"], "${GITHUB_POSTGRESQL_PORT:5432}")
        self.assertNotIn("infra-mysql-host", shared)
        sql = yaml_json((CONFIG / "mybatis-plus.yaml").read_text())["mybatis-plus"]["global-config"]["db-config"]
        self.assertEqual(sql["logic-delete-value"], "true")
        self.assertEqual(sql["logic-not-delete-value"], "false")


if __name__ == "__main__":
    unittest.main()
