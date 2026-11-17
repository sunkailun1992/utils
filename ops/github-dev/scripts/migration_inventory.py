#!/usr/bin/env python3
"""按各服务 Flyway 迁移目录汇总每个库最终会有的表：版本按数字顺序，累加建表、减去删表；只读文件，不连数据库。

表结构只由各服务 src/main/resources/db/migration 的 Flyway 迁移管理，新库保持空库，首次启动时由 Flyway 建表；
本模块只给配置核对用，不生成或执行任何建表脚本。"""
import re
from pathlib import Path

SERVICES = ("ai", "ai-memory", "rag", "message", "user", "mcp", "report")
VERSION = re.compile(r"V(\d+)__.+\.sql")
CREATE = re.compile(r"CREATE TABLE(?: IF NOT EXISTS)?\s+(?:public\.)?([a-z][a-z0-9_]*)\s*\(", re.I)
DROP = re.compile(r"DROP TABLE(?: IF EXISTS)?\s+(?:public\.)?([a-z][a-z0-9_]*)\s*;", re.I)
DO_BLOCK = re.compile(r"DO \$\$(.*?)\$\$", re.S)
ARRAY = re.compile(r"ARRAY\s*\[([^\]]*)\]", re.I)


def version(path):
    """迁移文件的版本号；文件名不合 Flyway 规则时直接报错。"""
    match = VERSION.fullmatch(path.name)
    if not match:
        raise ValueError(f"迁移文件名不合规：{path}")
    return int(match.group(1))


def dropped(sql):
    """一份迁移里删掉的表：直接写的 DROP TABLE，以及 DO 块里按数组逐个动态删的表。"""
    tables = {name.lower() for name in DROP.findall(sql)}
    for block in DO_BLOCK.findall(sql):
        if "DROP TABLE" in block.upper():
            for items in ARRAY.findall(block):
                tables.update(name.lower() for name in re.findall(r"'([a-z][a-z0-9_]*)'", items, re.I))
    return tables


def migration_inventory(root):
    """返回 {服务: 迁移执行完后的表名集合}；服务没有迁移文件时报错，不当作零张表。"""
    inventory = {}
    for service in SERVICES:
        folder = Path(root) / service / "src/main/resources/db/migration"
        files = sorted(folder.glob("V*__*.sql"), key=version) if folder.is_dir() else []
        if not files:
            raise FileNotFoundError(f"{service} 没有 Flyway 迁移：{folder}")
        tables = set()
        for file in files:
            sql = file.read_text()
            tables.update(name.lower() for name in CREATE.findall(sql))
            tables.difference_update(dropped(sql))
        inventory[service] = tables
    return inventory
