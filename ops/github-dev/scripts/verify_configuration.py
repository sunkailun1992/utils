#!/usr/bin/env python3
"""检查 GitHub 环境隔离边界、配置覆盖和代码所需数据表。只读，不连接远端。"""
import re
import argparse
import json
import subprocess
import sys
from pathlib import Path
from prepare_databases import prepare
from render_nacos import yaml_json

ROOT = Path(__file__).resolve().parents[4]
SERVICES = ("gateway", "user", "message", "report", "ai", "ai-agent", "ai-memory", "rag", "mcp")
CONFIG = Path(__file__).resolve().parents[1] / "nacos/DEFAULT_GROUP"

# 与受审计的两级Collector使用同一字段合同，仍对完整树/类型作精确匹配。
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "observability"))
from privacy_contract import LOG_FIELDS

APPROVED_STRUCTURED_INCLUDE = ",".join((
    "@timestamp", "log", "log.level", "service", "service.name", "service.version", "service.environment", *LOG_FIELDS))


def import_dataid(item):
    match = re.fullmatch(r"(?:optional:)?nacos:([^?]+)(?:\?.*)?", item)
    assert match, "只接受显式 Nacos dataId 导入"
    return match[1]


def check_catalog(config, catalog, retired, consumers):
    assert catalog["namespace"] == retired["namespace"] == "github-dev"
    assert catalog["group"] == retired["group"] == "DEFAULT_GROUP"
    entries = {entry["dataId"]: entry for entry in catalog["entries"]}
    assert len(entries) == len(catalog["entries"]), "catalog 存在重复 dataId"
    assert set(entries) == set(config) == set(consumers), "模板、catalog 与实际导入未对齐"
    old = {entry["dataId"] for entry in retired["retired"]}
    assert len(old) == len(retired["retired"]) and not old & set(entries), "旧项重复或仍在模板目录"
    for item in retired["retired"]:
        assert item["replacement"] is None or item["replacement"] in entries, "旧项替代配置缺失"
    for name, entry in entries.items():
        text = config[name]
        assert set(entry["consumers"]) == consumers[name], name + " 消费者说明未同步"
        assert entry["description"].strip() and entry["category"].strip(), name + " 缺少分类或说明"
        assert f"# dataId：{name} | group：DEFAULT_GROUP | namespace：github-dev" in text, name
        assert f"# 配置分类：{entry['category']}" in text, name
        purpose = re.search(r"^# 用途：(.+)$", text, re.M)
        assert purpose and entry["description"].startswith(purpose[1].rstrip("。")), name + " 说明与注释未同步"
        assert f"# 导入服务：{'、'.join(entry['consumers'])}。" in text, name


def check_model_credential_scope(config, catalog, retired):
    """历史共享模型项不再发布或自动删除；Google 参数由 Agent 显式持有。"""
    legacy = "ai-model-secrets.yaml"
    assert legacy not in config and legacy not in {item["dataId"] for item in catalog["entries"]}, "历史共享模型不得重新进入活跃模板或 catalog"
    assert legacy not in {item["dataId"] for item in retired["retired"]}, "历史共享模型远端保留，不得自动删除"
    agent = yaml_json(config["ai-agent-spring.yaml"])
    google = agent["spring"]["ai"]["google"]["genai"]
    assert google["api-key"] == "${GITHUB_GOOGLE_API_KEY}", "Agent Google 凭据须由本服务私有占位符提供"
    assert google["chat"]["options"]["model"] == "${GITHUB_GOOGLE_CHAT_MODEL:gemini-3.1-flash-lite}", "Agent Google 模型占位符或默认值改变"
    assert agent["ai"]["agent"]["google"]["base-url"] == "${GITHUB_GOOGLE_BASE_URL:}", "Agent Google 原生基址必须可选且默认空值"


def check_parameter_comments(source):
    """通过 YAML 节点定位参数，避免把字符串中的冒号误识别为键。"""
    result = subprocess.run(["ruby", "-ryaml", "-rjson", "-e", """
      rows = []
      visit = lambda do |node|
        if node.is_a?(Psych::Nodes::Mapping)
          node.children.each_slice(2) do |key, value|
            if value.is_a?(Psych::Nodes::Scalar) || value.is_a?(Psych::Nodes::Sequence)
              rows << key.start_line
            end
            visit.call(value)
          end
        else
          (node.children || []).each { |child| visit.call(child) }
        end
      end
      visit.call(Psych.parse_stream(STDIN.read))
      puts JSON.generate(rows)
    """], input=source, capture_output=True, text=True)
    assert result.returncode == 0, "YAML 无法解析；原文不输出"
    lines = source.splitlines()
    for line in json.loads(result.stdout):
        previous = line - 1
        while previous >= 0 and not lines[previous].strip():
            previous -= 1
        assert previous >= 0 and lines[previous].lstrip().startswith("#"), f"参数缺少说明，行 {line + 1}"


def effective_configuration(imports, templates):
    """合并映射并保留列表整体及标量类型，用于核对配置移动前后的有效值。"""
    def merge(left, right):
        if not isinstance(left, dict) or not isinstance(right, dict):
            return right
        combined = dict(left)
        for key, value in right.items():
            combined[key] = merge(combined[key], value) if key in combined else value
        return combined
    result = {}
    for item in imports:
        result = merge(result, yaml_json(templates[import_dataid(item)]))
    return result


def check_consolidation_baseline(baseline_file):
    """核对 Git 外的整理前模板快照；不接触真实 Nacos 值，不连接服务。"""
    before = json.loads(baseline_file.read_text())
    after = {file.name: file.read_text() for file in CONFIG.iterdir()}
    retired = json.loads((CONFIG.parent / "retired-dataids.json").read_text())["retired"]
    removed = {item["dataId"] for item in retired}
    expected_profiles = {f"{service}/src/main/resources/application-{environment}.yml"
                         for service in SERVICES for environment in ("dev", "test", "prod")}
    assert set(before["profiles"]) == expected_profiles, "旧快照必须覆盖全部 27 个 profile"
    assert set(after) <= set(before["templates"]) and set(before["templates"]) - set(after) <= removed, "旧快照包含缺失或未经登记的 dataId"
    for relative, original in before["profiles"].items():
        old = yaml_json(original)
        new = yaml_json((ROOT / relative).read_text())
        old_imports = old["spring"]["config"].pop("import")
        new_imports = new["spring"]["config"].pop("import")
        assert json.dumps(old, sort_keys=True) == json.dumps(new, sort_keys=True), relative + " 引导键发生变化"
        assert new_imports == [item for item in old_imports if import_dataid(item) not in removed], relative + " 导入次序或选项改变"
        old_ids = [import_dataid(item) for item in old_imports]
        for entry in retired:
            if entry["dataId"] not in old_ids:
                continue
            if entry.get("kind") == "monitoring-retirement":
                continue
            if entry["replacement"] is None:
                assert yaml_json(before["templates"][entry["dataId"]]) == {}, "移除项不是空映射"
            else:
                index = old_ids.index(entry["dataId"])
                assert old_ids[index + 1] == entry["replacement"], "被合并项原先并不相邻"
                assert old_imports[index].replace(entry["dataId"], entry["replacement"], 1) == old_imports[index + 1], "导入刷新或必需性不同"
        old_effective = effective_configuration(old_imports, before["templates"])
        new_effective = effective_configuration(new_imports, after)
        if "rag-spring.yaml" in [import_dataid(item) for item in new_imports]:
            hybrid = new_effective["rag"]["vector"].get("hybrid-write-enabled")
            if "hybrid-write-enabled" not in old_effective.get("rag", {}).get("vector", {}):
                new_effective["rag"]["vector"].pop("hybrid-write-enabled")
            assert hybrid == "${GITHUB_RAG_HYBRID_WRITE_ENABLED:false}", "新增混合写入开关必须默认关闭"
        # 本批明确退役的配置可变化；其余仍按逐键/类型/列表顺序比较。
        old_management = old_effective.get("management", {})
        if "tracing" in old_management:
            assert old_management.pop("tracing") == {"enabled": False}, "旧 tracing 配置不是已知关闭值"
        old_spring = old_effective.get("spring", {})
        if "boot" in old_spring:
            assert old_spring.pop("boot") == {"admin": {"client": {"enabled": False}}}, "旧 Admin 配置不是已知关闭值"
        new_management = new_effective.get("management", {})
        assert new_management["endpoints"]["web"]["exposure"]["include"] == "health,info,prometheus"
        new_management["endpoints"]["web"]["exposure"]["include"] = "health,info"
        approved_metrics = {
            "tags": {"application": "${spring.application.name}", "environment": "github-dev"},
            "distribution": {"percentiles-histogram": {"http.server.requests": True}},
        }
        # 精确叶子与 JSON 类型匹配：拒绝全局 histogram、其它 meter 和 1/字符串冒充布尔值。
        assert json.dumps(new_management.pop("metrics"), sort_keys=True) == json.dumps(approved_metrics, sort_keys=True), "metrics 存在未经核准的键、类型或值"
        for logger in ("org.springframework.ai.chat.model.MessageAggregator",
                       "org.springframework.ai.model.observation.ErrorLoggingObservationHandler"):
            assert new_effective["logging"]["level"].pop(logger) == "OFF", "Spring AI敏感异常日志必须关闭"
        logging = new_effective["logging"].pop("structured")
        approved_logging = {
            "ecs": {"service": {"name": "${spring.application.name:unknown}",
                                  "version": "${spring.application.version:unknown}",
                                  "environment": "github-dev"}},
            "format": {"console": "ecs"},
            "json": {"include": APPROVED_STRUCTURED_INCLUDE,
                     "context": {"include": False}},
        }
        # 只豁免完整匹配的新增树；额外输出、JSON规则、键和类型变化都必须失败。
        assert json.dumps(logging, sort_keys=True) == json.dumps(approved_logging, sort_keys=True), "structured logging 存在未经核准的键、类型或值"
        if "rag" in old_effective:
            for key in ("retrieval-enabled", "graph-enabled"):
                assert old_effective["rag"]["engine"].pop(key, False) is False, "旧 Engine 开关不是已知关闭值"
        assert json.dumps(old_effective, sort_keys=True) == json.dumps(new_effective, sort_keys=True), relative + " 未授权有效配置变化"
    print(f"PASS: {len(before['profiles'])} profile 的参数类型、值、列表及保留导入次序等价；仅允许已核混合写入与观测/旧RAG退役差异")


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
    consumers = {}
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
            imports = value["spring"]["config"]["import"]
            ids = [import_dataid(item) for item in imports]
            assert len(ids) == len(set(ids)), file
            imported.update(ids)
            for name in ids:
                consumers.setdefault(name, set()).add(service)
    assert imported == {file.name for file in CONFIG.iterdir()}, imported
    for file in CONFIG.iterdir():
        yaml_json(file.read_text())
        check_parameter_comments(file.read_text())
        assert not re.search(r"cfbf4c42-|meilianshuke\.com|hz2y\.com", file.read_text()), file
    check_catalog({file.name: file.read_text() for file in CONFIG.iterdir()},
                  json.loads((CONFIG.parent / "catalog.json").read_text()),
                  json.loads((CONFIG.parent / "retired-dataids.json").read_text()), consumers)
    check_model_credential_scope({file.name: file.read_text() for file in CONFIG.iterdir()},
                                 json.loads((CONFIG.parent / "catalog.json").read_text()),
                                 json.loads((CONFIG.parent / "retired-dataids.json").read_text()))
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
    documents, manifest = prepare(ROOT)
    sql = "\n".join(documents.values())
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, help="Git 外的整理前模板/profile JSON 快照")
    args = parser.parse_args()
    check()
    if args.baseline:
        check_consolidation_baseline(args.baseline)
