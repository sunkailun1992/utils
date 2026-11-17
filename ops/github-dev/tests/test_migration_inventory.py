"""表清单按各服务 Flyway 迁移目录计算：版本按数字顺序，累加建表、减去删表（含 DO 块里按数组删表）；只读文件，不连数据库。"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from migration_inventory import SERVICES, migration_inventory
from verify_configuration import check_entity_tables

DYNAMIC_DROP = """DO $$
DECLARE
    target text;
BEGIN
    FOREACH target IN ARRAY ARRAY['fixture_b', 'fixture_c'] LOOP
        EXECUTE format('DROP TABLE %I', target);
    END LOOP;
END $$;
"""


class MigrationInventoryTests(unittest.TestCase):
    def fixture(self, directory, migrations):
        """每个服务一个迁移目录；未单独给的服务只有 undo_log。"""
        root = Path(directory)
        for service in SERVICES:
            folder = root / service / "src/main/resources/db/migration"
            folder.mkdir(parents=True)
            for name, sql in migrations.get(service, {"V1__baseline.sql": "CREATE TABLE IF NOT EXISTS undo_log (id int);\n"}).items():
                (folder / name).write_text(sql)
        return root

    def test_dropped_tables_are_not_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, {"user": {
                "V1__baseline.sql": "CREATE TABLE IF NOT EXISTS fixture_a (id int);\nCREATE TABLE IF NOT EXISTS fixture_b (id int);\n",
                "V5__drop_b.sql": "DROP TABLE IF EXISTS fixture_b;\n"}})
            self.assertEqual(migration_inventory(root)["user"], {"fixture_a"})

    def test_tables_dropped_in_a_do_block_array_are_not_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, {"ai": {
                "V1__baseline.sql": "".join(f"CREATE TABLE IF NOT EXISTS fixture_{x} (id int);\n" for x in "abc"),
                "V4__drop_unused_tables.sql": DYNAMIC_DROP}})
            self.assertEqual(migration_inventory(root)["ai"], {"fixture_a"})

    def test_versions_are_applied_in_numeric_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, {"message": {
                "V2__create_c.sql": "CREATE TABLE fixture_c (id int);\n",
                "V10__drop_c.sql": "DROP TABLE fixture_c;\n",
                "V1__baseline.sql": "CREATE TABLE IF NOT EXISTS undo_log (id int);\n"}})
            self.assertEqual(migration_inventory(root)["message"], {"undo_log"})

    def test_service_without_migrations_fails_instead_of_counting_zero_tables(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, {})
            for file in (root / "rag/src/main/resources/db/migration").iterdir():
                file.unlink()
            with self.assertRaises(FileNotFoundError):
                migration_inventory(root)

    def test_entity_table_without_migration_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, {"user": {"V1__baseline.sql": "CREATE TABLE IF NOT EXISTS fixture_a (id int);\n"}})
            source = root / "user/src/main/java/com/kellen/Fixture.java"
            source.parent.mkdir(parents=True)
            source.write_text('@TableName("fixture_a")\nclass A {}\n@TableName("fixture_missing")\nclass B {}\n')
            with self.assertRaisesRegex(AssertionError, "fixture_missing"):
                check_entity_tables(root, migration_inventory(root))
            source.write_text('@TableName("fixture_a")\nclass A {}\n')
            check_entity_tables(root, migration_inventory(root))


if __name__ == "__main__":
    unittest.main()
