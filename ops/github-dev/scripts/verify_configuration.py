#!/usr/bin/env python3
"""检查 GitHub 环境隔离边界、配置覆盖和代码所需数据表。只读，不连接远端。"""
import re
from pathlib import Path
from prepare_databases import prepare
from render_nacos import yaml_json

ROOT = Path(__file__).resolve().parents[4]
SERVICES = ("gateway", "user", "message", "report", "ai", "ai-agent", "ai-memory", "rag", "mcp")
CONFIG = Path(__file__).resolve().parents[1] / "nacos/DEFAULT_GROUP"


def check():
    imported = set()
    for service in SERVICES:
        resources = ROOT / service / "src/main/resources"
        main = yaml_json((resources / "application.yml").read_text())
        assert main["spring"]["profiles"]["active"] == "${APP_ENV:dev}", service
        for environment in ("dev", "test", "prod"):
            file = resources / f"application-{environment}.yml"
            text = file.read_text()
            value = yaml_json(text)
            assert value["custom"]["namespace"] == (
                "${GITHUB_NACOS_" + environment.upper() + "_NAMESPACE:github-" + environment + "}"), file
            assert "cfbf4c42-" not in text and "${NACOS_NAMESPACE" not in text, file
            nacos = value["spring"]["cloud"]["nacos"]
            for kind in ("config", "discovery"):
                assert nacos[kind]["namespace"] == "${custom.namespace}", file
                assert nacos[kind]["server-addr"] == "${custom.nacos-ip}", file
            imported.update(re.findall(r"nacos:([^?\"\n]+)", text))
    assert imported == {file.name for file in CONFIG.iterdir()}, imported
    for file in CONFIG.iterdir():
        yaml_json(file.read_text())
        assert not re.search(r"cfbf4c42-|meilianshuke\.com|hz2y\.com", file.read_text()), file
    storage = yaml_json((CONFIG / "storage.yaml").read_text())["dromara"]["x-file-storage"]
    assert storage["default-platform"] == "minio-private"
    assert storage["minio"][0]["enable-storage"] is True
    assert "aliyun-oss" not in storage and "tencent-cos" not in storage
    auth = yaml_json((CONFIG / "security-auth.yaml").read_text())
    assert auth["security"]["auth"]["header-enabled"] is False
    assert auth["tenant"]["ignore-without-tenant"] is False
    sql, manifest = prepare(ROOT)
    assert not re.search(r"INSERT(?: IGNORE)? INTO auth_user\b", sql, re.I)
    assert not re.search(r"meilianshuke\.com|hz2y\.com", sql)
    for service, inventory in manifest.items():
        used = set()
        for file in (ROOT / service / "src/main/java").rglob("*.java"):
            used.update(re.findall(r'@TableName\([^\n]*?"([^"\n]+)"', file.read_text()))
        assert used <= set(inventory["tables"]), (service, used - set(inventory["tables"]))
    print(f"PASS: {len(SERVICES)} 服务 / 27 profile / {len(imported)} dataId / "
          f"{sum(len(v['tables']) for v in manifest.values())} 所需表；隔离和业务种子门禁通过")


if __name__ == "__main__":
    check()
