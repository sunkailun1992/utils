"""个人 PG 模板与结构初始化合同；不连接数据库，也不创建人员或权限数据。"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from prepare_databases import prepare
from render_nacos import yaml_json
from verify_configuration import check_parameter_comments

ROOT = Path(__file__).resolve().parents[4]
CONFIG = ROOT / "utils/ops/github-dev/nacos/DEFAULT_GROUP"
SERVICES = ("ai", "ai-memory", "mcp", "message", "rag", "report", "user")


class PostgresqlConfigurationTests(unittest.TestCase):
    def structure_fixture(self, directory):
        """独立仓 CI 只需临时 DDL；真实七仓表清单由集成命令另验。"""
        root = Path(directory)
        common = root / "utils/src/main/resources/db/postgresql/common-infra-schema.sql"
        common.parent.mkdir(parents=True)
        common.write_text((ROOT / "utils/src/main/resources/db/postgresql/common-infra-schema.sql").read_text())
        for service, name, count in (("ai", "ai-schema.sql", 1),
                                     ("ai-memory", "ai-memory-schema.sql", 1),
                                     ("message", "message-schema.sql", 1),
                                     ("user", "user-schema.sql", 1)):
            folder = root / service / "src/main/resources/db/postgresql"
            folder.mkdir(parents=True)
            folder.joinpath(name).write_text("\n".join(
                f"CREATE TABLE IF NOT EXISTS public.fixture_{i} (id BIGINT PRIMARY KEY);"
                for i in range(count)))
        (root / "user/src/main/resources/db/postgresql/user-bootstrap.sql").write_text(
            "INSERT INTO must_not_read VALUES (1);\n")
        return root

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
        with tempfile.TemporaryDirectory() as directory:
            documents, manifest = prepare(self.structure_fixture(directory))
        self.assertEqual(set(documents), {"github_dev_" + name.replace("-", "_") for name in SERVICES})
        self.assertEqual(set(manifest), set(SERVICES))
        self.assertEqual(sum(len(row["tables"]) for row in manifest.values()), 18)
        for database, source in documents.items():
            with self.subTest(database=database):
                self.assertIn("CREATE TABLE IF NOT EXISTS public.undo_log", source)
                self.assertNotRegex(source, r"(?im)^\s*(?:USE|CREATE DATABASE|INSERT|UPDATE|DELETE|TRUNCATE|DROP)\b")
                self.assertNotIn("ENGINE=InnoDB", source)
                self.assertNotIn("AUTO_INCREMENT", source)
                self.assertNotIn("user-bootstrap.sql", "\n".join(manifest[database.removeprefix("github_dev_").replace("_", "-")]["sources"]))

    def test_structure_preparation_rejects_record_mutation_and_mysql_syntax(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.structure_fixture(directory)
            target = root / "ai/src/main/resources/db/postgresql/ai-schema.sql"
            for source in ("INSERT INTO fixture VALUES (1);", "UPDATE fixture SET id=1;",
                           "DELETE FROM fixture;", "DROP TABLE fixture;", "TRUNCATE fixture;", "USE fixture;",
                           "CREATE DATABASE fixture;", "CREATE TABLE fixture (id INT AUTO_INCREMENT);",
                           "CREATE TABLE fixture (id INT) ENGINE=InnoDB;"):
                with self.subTest(source=source):
                    target.write_text(source)
                    with self.assertRaises(ValueError):
                        prepare(root)

    def test_structure_preparation_does_not_silently_skip_missing_business_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.structure_fixture(directory)
            (root / "ai/src/main/resources/db/postgresql/ai-schema.sql").unlink()
            with self.assertRaises(FileNotFoundError):
                prepare(root)


if __name__ == "__main__":
    unittest.main()
