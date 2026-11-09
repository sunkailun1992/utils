"""个人 PG 模板与结构初始化合同；不连接数据库，也不创建人员或权限数据。"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from prepare_databases import prepare
from render_nacos import yaml_json, render_document
from verify_configuration import check_parameter_comments

ROOT = Path(__file__).resolve().parents[4]
CONFIG = ROOT / "utils/ops/github-dev/nacos/DEFAULT_GROUP"
SERVICES = ("ai", "ai-memory", "mcp", "message", "rag", "report", "user")


class PostgresqlConfigurationTests(unittest.TestCase):
    def test_each_service_has_its_own_postgresql_role_and_no_active_mysql(self):
        for service in SERVICES:
            with self.subTest(service=service):
                source = (CONFIG / f"{service}-spring.yaml").read_text()
                value = yaml_json(source)
                self.assertNotIn("mysql", value)
                self.assertNotIn("mysql-gray", value)
                primary = value["postgresql"]
                suffix = service.replace("-", "_")
                prefix = "GITHUB_POSTGRESQL_" + suffix.upper()
                self.assertTrue(primary["url"].startswith("jdbc:postgresql://"))
                self.assertIn(f"/github_dev_{suffix}", primary["url"])
                self.assertEqual(primary["username"], "${" + prefix + "_USERNAME}")
                self.assertEqual(primary["password"], "${" + prefix + "_PASSWORD}")
                self.assertEqual(primary["driverClassName"], "org.postgresql.Driver")
                self.assertEqual(value["postgresql-gray"], {
                    key: "${postgresql." + key + "}" for key in primary})
                check_parameter_comments(source)

    def test_shared_connection_and_boolean_sql_contract_are_postgresql(self):
        shared = yaml_json((CONFIG / "reuse-configuration.yaml").read_text())["custom"]
        self.assertEqual(shared["infra-postgresql-host"], "${GITHUB_POSTGRESQL_HOST}")
        self.assertEqual(shared["infra-postgresql-port"], "${GITHUB_POSTGRESQL_PORT:5432}")
        self.assertNotIn("infra-mysql-host", shared)
        sql = yaml_json((CONFIG / "mybatis-plus.yaml").read_text())["mybatis-plus"]["global-config"]["db-config"]
        self.assertEqual(sql["logic-delete-value"], "true")
        self.assertEqual(sql["logic-not-delete-value"], "false")

    def test_structure_preparation_is_separate_per_database_and_has_no_seed_dml(self):
        documents, manifest = prepare(ROOT)
        self.assertEqual(set(documents), {"github_dev_" + name.replace("-", "_") for name in SERVICES})
        self.assertEqual(set(manifest), set(SERVICES))
        self.assertEqual(sum(len(row["tables"]) for row in manifest.values()), 50)
        for database, source in documents.items():
            with self.subTest(database=database):
                self.assertIn("CREATE TABLE IF NOT EXISTS public.undo_log", source)
                self.assertNotRegex(source, r"(?im)^\s*(?:USE|CREATE DATABASE|INSERT|UPDATE|DELETE|TRUNCATE|DROP)\b")
                self.assertNotIn("ENGINE=InnoDB", source)
                self.assertNotIn("AUTO_INCREMENT", source)
                self.assertNotIn("user-bootstrap.sql", "\n".join(manifest[database.removeprefix("github_dev_").replace("_", "-")]["sources"]))


if __name__ == "__main__":
    unittest.main()
