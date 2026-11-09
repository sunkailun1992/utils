#!/usr/bin/env python3
"""生成逐库 PostgreSQL 结构脚本；不建数据库、不写种子、不读取业务记录。"""
import argparse
import json
import re
from pathlib import Path

SCRIPTS = {
    "ai": ["ai-schema.sql"],
    "ai-memory": ["ai-memory-schema.sql"],
    "rag": [],
    "message": ["message-schema.sql"],
    "user": ["user-schema.sql"],
    "mcp": [],
    "report": [],
}


def prepare(root):
    documents, manifest = {}, {}
    common = root / "utils/src/main/resources/db/postgresql/common-infra-schema.sql"
    for service, names in SCRIPTS.items():
        database = "github_dev_" + service.replace("-", "_")
        tables, parts = set(), []
        sources = [common] + [root / service / "src/main/resources/db/postgresql" / name for name in names]
        for source in sources:
            text = source.read_text()
            if re.search(r"(?im)^\s*(?:USE|CREATE DATABASE|INSERT|UPDATE|DELETE|DROP|TRUNCATE)\b", text):
                raise ValueError("PostgreSQL structure preparation must not create databases or mutate records")
            if re.search(r"\b(?:AUTO_INCREMENT|ENGINE\s*=\s*InnoDB)\b", text, re.I):
                raise ValueError("MySQL syntax cannot be used in PostgreSQL structure preparation")
            declared = re.findall(r"CREATE TABLE IF NOT EXISTS\s+(?:public\.)?([a-z][a-z0-9_]*)\s*\(", text, re.I)
            if tables.intersection(declared):
                raise ValueError("Duplicate table declaration in PostgreSQL structure sources")
            tables.update(declared)
            parts.append(text)
        manifest[service] = {"database": database, "schema": "public", "tables": sorted(tables),
                             "sources": [str(source.relative_to(root)) for source in sources]}
        documents[database] = "\n\n".join(parts) + "\n"
    return documents, manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repos-root", type=Path, default=Path(__file__).resolve().parents[4])
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    documents, inventory = prepare(args.repos_root)
    args.output.mkdir(parents=True, exist_ok=True)
    for database, text in documents.items():
        (args.output / f"{database}.sql").write_text(text)
    (args.output / "database-manifest.json").write_text(
        json.dumps(inventory, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({row["database"]: len(row["tables"]) for row in inventory.values()}))
