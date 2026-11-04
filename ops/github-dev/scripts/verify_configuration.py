#!/usr/bin/env python3
"""检查 GitHub 环境隔离边界、配置覆盖和代码所需数据表。只读，不连接远端。"""
import re
from pathlib import Path
from prepare_databases import prepare
from render_nacos import yaml_json

ROOT = Path(__file__).resolve().parents[4]
SERVICES = ("gateway", "user", "message", "report", "ai", "ai-agent", "ai-memory", "rag", "mcp")
CONFIG = Path(__file__).resolve().parents[1] / "nacos/DEFAULT_GROUP"


def route_destination(path, routes):
    for route in sorted(routes, key=lambda item: item.get("order", 0)):
        patterns = [pattern for predicate in route["predicates"]
                    if predicate.startswith("Path=")
                    for pattern in predicate.removeprefix("Path=").split(",")]
        if not any(path == pattern or pattern.endswith("/**") and
                   (path == pattern[:-3] or path.startswith(pattern[:-2]))
                   for pattern in patterns):
            continue
        downstream = path
        for rule in route.get("filters", []):
            if not isinstance(rule, str) or not rule.startswith("StripPrefix="):
                raise AssertionError("未支持的路径过滤器，需要扩展契约检查：" + route["id"])
            count = int(rule.split("=", 1)[1])
            downstream = "/" + "/".join(downstream.lstrip("/").split("/")[count:])
        return route["uri"], downstream
    raise AssertionError("没有网关路由：" + path)


def check_route(path, service, controller_roots, routes):
    target, downstream = route_destination(path, routes)
    assert target == "lb://" + service, (path, target, service)
    assert any(downstream == base or downstream.startswith(base.rstrip("/") + "/")
               for base in controller_roots), (path, downstream, service)


def check_frontend_routes(root, routes):
    constants = (root / "admin-web/src/constants/auth.ts").read_text()
    modules = {"auth": ("USER", "user"), "message": ("MESSAGE", "message"),
               "ai": ("AI", "ai"), "rag": ("RAG", "rag")}
    checked = 0
    for module, (constant, service) in modules.items():
        prefix = re.search(r"export const " + constant + r"_SERVICE_PREFIX = ['\"]([^'\"]*)['\"]", constants)
        assert prefix, constant
        roots = set()
        for file in (root / service / "src/main/java").rglob("*.java"):
            source = file.read_text()
            if "@RestController" in source:
                match = re.search(r'@RequestMapping\(\s*(?:(?:value|path)\s*=\s*)?"([^"\n]+)"', source)
                if match:
                    roots.add(match[1])
        source = (root / f"admin-web/src/services/{module}/index.ts").read_text()
        paths = {match[1] for match in re.findall(r"(['\"`])(/(?:auth|api|messages)/[^'\"`\n]*)\1", source)}
        assert paths and roots, module
        for path in paths:
            check_route(prefix[1] + path, service, roots, routes)
            checked += 1
    return checked


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
            assert value["custom"]["namespace"] == "${GITHUB_NACOS_DEV_NAMESPACE:github-dev}", file
            assert value["custom"]["nacos-ip"].startswith(
                "${GITHUB_NACOS_DEV_SERVER_ADDR:${GITHUB_NACOS_SERVER_ADDR:"), file
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
    gateway = yaml_json((CONFIG / "gateway-spring.yaml").read_text())
    routes = gateway["spring"]["cloud"]["gateway"]["server"]["webflux"]["routes"]
    frontend_paths = check_frontend_routes(ROOT, routes)
    storage = yaml_json((CONFIG / "storage.yaml").read_text())["dromara"]["x-file-storage"]
    assert storage["default-platform"] == "minio-private"
    assert storage["minio"][0]["enable-storage"] is True
    assert "aliyun-oss" not in storage and "tencent-cos" not in storage
    auth = yaml_json((CONFIG / "security-auth.yaml").read_text())
    assert auth["security"]["auth"]["header-enabled"] is False
    assert auth["tenant"]["ignore-without-tenant"] is False
    elasticsearch = yaml_json((CONFIG / "elasticsearch.yaml").read_text())
    assert elasticsearch["elasticsearch"]["request-log-index"] == "github_dev_request_log"
    sql, manifest = prepare(ROOT)
    assert not re.search(r"INSERT(?: IGNORE)? INTO auth_user\b", sql, re.I)
    assert not re.search(r"meilianshuke\.com|hz2y\.com", sql)
    for service, inventory in manifest.items():
        used = set()
        for file in (ROOT / service / "src/main/java").rglob("*.java"):
            used.update(re.findall(r'@TableName\([^\n]*?"([^"\n]+)"', file.read_text()))
        assert used <= set(inventory["tables"]), (service, used - set(inventory["tables"]))
    print(f"PASS: {len(SERVICES)} 服务 / 27 profile / {len(imported)} dataId / "
          f"{sum(len(v['tables']) for v in manifest.values())} 所需表 / "
          f"{frontend_paths} 前端路径；隔离、路由和业务种子门禁通过")


if __name__ == "__main__":
    check()
