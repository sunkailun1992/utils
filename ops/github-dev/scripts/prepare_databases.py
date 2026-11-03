#!/usr/bin/env python3
"""从各仓库权威 DDL 生成个人开发库初始化，不读取或复制公司数据。"""
import argparse
import json
import re
from pathlib import Path

SCRIPTS = {
    "ai": ["ai-init.sql"],
    "ai-memory": ["ai-memory-init.sql"],
    "rag": ["rag-knowledge-graph-schema.sql"],
    "message": ["message-schema.sql"],
    "user": ["auth-schema.sql", "auth-external-identity-schema.sql", "auth-oauth-client-schema.sql"],
    "mcp": [],
    "report": [],
}


def permission_seeds(root):
    statements = []
    for name in ["auth-schema.sql", "auth-rag-resource-schema.sql", "auth-rag-graph-resource-schema.sql"]:
        source = (root / "user/src/main/resources/db" / name).read_text()
        for statement in re.split(r";\s*(?:\n|$)", source):
            statement = statement.lstrip()
            while statement.startswith(("--", "/*")):
                stripped = re.sub(r"^(?:--[^\n]*(?:\n|$)|/\*.*?\*/)\s*", "", statement,
                                  count=1, flags=re.S)
                if stripped == statement:
                    raise ValueError("Unterminated SQL comment")
                statement = stripped
            if re.match(r"INSERT IGNORE INTO (?:auth_resource|auth_role_resource)\b", statement, re.I):
                statements.append(statement.strip() + ";")
    return statements


def prepare(root):
    sql, manifest = [], {}
    common = root / "utils/src/main/resources/db/common-infra-schema.sql"
    for service, names in SCRIPTS.items():
        schema = "github_dev_" + service.replace("-", "_")
        definitions = {}
        sources = [common] + [root / service / "src/main/resources/db" / name for name in names]
        for source in sources:
            blocks = re.findall(
                r"CREATE TABLE IF NOT EXISTS\s+`?\w+`?\s*\(.*?\) ENGINE=InnoDB[^;]*;",
                source.read_text(), re.S | re.I)
            for block in blocks:
                table = re.search(r"EXISTS\s+`?(\w+)", block, re.I).group(1)
                # Some service-owned scripts include common infrastructure too.
                definitions.setdefault(table, block)
        manifest[service] = {"schema": schema, "tables": sorted(definitions),
                             "sources": [str(source.relative_to(root)) for source in sources]}
        # No IF NOT EXISTS here: refuse an existing schema instead of mixing unknown data.
        sql += [f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;",
                f"USE `{schema}`;"] + list(definitions.values())
    sql += ["USE `github_dev_ai`;",
            (root / "ai/src/main/resources/db/ai-file-storage-platform.sql").read_text()]
    sql += ["USE `github_dev_user`;",
            "INSERT INTO auth_tenant (id,name,code,description,tenant_id) "
            "VALUES ('100','GitHub 开发','github-dev','个人独立开发租户','100');",
            "INSERT INTO auth_role (id,name,code,data_scope,tenant_id) "
            "VALUES ('r_admin_100','开发管理员','admin','ALL','100');"]
    # 只初始化权限，不创建人员账户或外部 OAuth 客户端。
    sql += permission_seeds(root)
    result = "\n\n".join(sql) + "\n"
    if re.search(r"(?:^|;)\s*(?:--[^\n]*\n\s*)*(?:DROP|TRUNCATE|DELETE|GRANT|REVOKE)\b",
                 result, re.I):
        raise ValueError("Unexpected destructive or permission SQL")
    return result, manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repos-root", type=Path,
                        default=Path(__file__).resolve().parents[4])
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    text, inventory = prepare(args.repos_root)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "initialize-databases.sql").write_text(text)
    (args.output / "database-manifest.json").write_text(
        json.dumps(inventory, ensure_ascii=False, indent=2) + "\n")
    (args.output / "permission-seeds.sql").write_text(
        "USE `github_dev_user`;\n" + "\n\n".join(permission_seeds(args.repos_root)) + "\n")
    print(json.dumps({row["schema"]: len(row["tables"]) for row in inventory.values()}))
