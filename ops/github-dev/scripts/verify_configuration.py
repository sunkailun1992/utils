#!/usr/bin/env python3
"""检查 GitHub 环境隔离边界、配置覆盖和代码所需数据表。只读，不连接远端。"""
import re
import argparse
import json
import subprocess
import sys
from pathlib import Path
from prepare_databases import prepare
from render_nacos import yaml_documents, yaml_json

ROOT = Path(__file__).resolve().parents[4]
SERVICES = ("gateway", "user", "message", "report", "ai", "ai-agent", "ai-memory", "rag", "mcp")
CONFIG = Path(__file__).resolve().parents[1] / "nacos/DEFAULT_GROUP"
LOCAL_CANARY_DATA_ID = "local-canary.yaml"
LOCAL_CANARY_IMPORT = f"nacos:{LOCAL_CANARY_DATA_ID}?refreshEnabled=false"
LOCAL_CANARY_RPC_SOURCES = {
    "ai-memory": {"[com.kellen.rpc.memory.MemoryRetrievalRpcService]": ["ai-agent"]},
    "rag": {"[com.kellen.rpc.rag.RagRetrievalRpcService]": ["ai"]},
    "user": {
        "[com.kellen.rpc.user.UserRpcService]": ["message"],
        "[com.kellen.rpc.auth.AuthOpenApiRpcService]": ["ai-agent"],
    },
}

# 与受审计的两级Collector使用同一字段合同，仍对完整树/类型作精确匹配。
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "observability"))
from privacy_contract import LOG_FIELDS

APPROVED_STRUCTURED_INCLUDE = ",".join((
    "@timestamp", "log", "log.level", "service", "service.name", "service.version", "service.environment", *LOG_FIELDS))


def import_dataid(item):
    match = re.fullmatch(r"(?:optional:)?nacos:([^?]+)(?:\?.*)?", item)
    assert match, "只接受显式 Nacos dataId 导入"
    return match[1]


def check_local_canary_profile(service, environment, documents):
    bootstrap = documents[0]
    imports = bootstrap["spring"]["config"]["import"]
    if environment != "dev":
        assert len(documents) == 1 and LOCAL_CANARY_DATA_ID not in map(import_dataid, imports), service
        return
    assert len(documents) == 2 and imports[-1] == LOCAL_CANARY_IMPORT, service
    override = documents[1]
    assert "config" not in override["spring"], service
    discovery = override["spring"]["cloud"]["nacos"]["discovery"]
    canary = override["traffic"]["governance"]["canary"]
    if service == "gateway":
        assert discovery == {"register-enabled": False}, service
        assert canary == {
            "enabled": "${local-canary.ready:false}",
            "routes": {backend: {"kellen-local": backend + "-canary-kellen-local"}
                       for backend in SERVICES if backend != "gateway"},
        }, service
        return
    assert discovery["service"] == "${spring.application.name}-canary-${local-canary.tag:kellen-local}", service
    assert discovery["register-enabled"] == "${local-canary.ready:false}", service
    assert discovery["metadata"] == {
        "[traffic.lane]": "${traffic.governance.instance.lane:canary}",
        "[canary.tag]": "${local-canary.tag:kellen-local}",
    }, service
    assert canary["require-grant"] is True, service
    assert canary.get("rpc-sources", {}) == LOCAL_CANARY_RPC_SOURCES.get(service, {}), service
    if service in LOCAL_CANARY_RPC_SOURCES:
        assert override["dubbo"]["provider"] == {
            "tag": "${local-canary.tag:kellen-local}", "export": "${local-canary.ready:false}",
        }, service
    else:
        assert "dubbo" not in override, service
    if service == "ai":
        assert override["ai"]["bootstrap"]["seed-demo-content"] is False, service
        assert override["ai"]["agent-client"]["canary"] == {
            "gateway-base-url": "${LOCAL_CANARY_GATEWAY_BASE_URL:http://127.0.0.1:7100}",
            "tags": "${local-canary.tag:kellen-local}",
        }, service
    if service == "ai-agent":
        assert override["ai"]["agent"]["registry"]["enabled"] is False, service
        assert override["ai"]["prompts"]["sync"]["enabled"] is False, service
    if service == "mcp":
        assert override["mcp"]["server"]["registry"]["enabled"] is False, service
        assert override["mcp"]["agent"]["registry"]["enabled"] is False, service


def check_local_canary_template(source):
    value = yaml_json(source)
    assert value == {
        "local-canary": {"tag": "${APP_CANARY_TAG:kellen-local}", "ready": True},
        "traffic": {"governance": {
            "instance": {"lane": "canary", "tag": "${local-canary.tag}"},
            "canary": {"enabled": True, "service": "${spring.application.name}",
                       "http-tag": "${local-canary.tag}",
                       "signing-key": "${GITHUB_PERSONAL_CANARY_SIGNING_KEY}"},
        }},
    }, "个人灰度模板含未核准的共享注册、鉴权或签名设置"
    # Python 比较会把数字 1 当作 True，另外检查布尔类型。
    assert value["local-canary"]["ready"] is True
    assert value["traffic"]["governance"]["canary"]["enabled"] is True


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


def check_storage_credential_scope(config, catalog, retired):
    active = {entry["dataId"] for entry in catalog["entries"]}
    removed = {entry["dataId"] for entry in retired["retired"]}
    assert "storage.yaml" not in config and "storage.yaml" not in active, "历史存储连接不得重新进入活动 Nacos"
    assert "storage.yaml" not in removed, "历史存储回滚配置不得自动删除"
    assert "oss" not in yaml_json(config["ai-spring.yaml"]).get("ai", {}), "AI 存储策略由控制台数据库维护"


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
    assert set(after) - set(before["templates"]) <= {LOCAL_CANARY_DATA_ID}, "新增项超出个人灰度 dataId"
    assert set(before["templates"]) - set(after) <= removed, "旧快照包含缺失或未经登记的 dataId"
    for relative, original in before["profiles"].items():
        old_documents = yaml_documents(original)
        new_documents = yaml_documents((ROOT / relative).read_text())
        old, new = old_documents[0], new_documents[0]
        added_canary = LOCAL_CANARY_DATA_ID not in before["templates"] and (
            new["spring"]["config"]["import"][-1:] == [LOCAL_CANARY_IMPORT])
        if added_canary:
            assert relative.endswith("application-dev.yml"), relative
            check_local_canary_profile(relative.split("/", 1)[0], "dev", new_documents)
        else:
            assert old_documents[1:] == new_documents[1:], relative + " 附加引导文档发生变化"
        old_imports = old["spring"]["config"].pop("import")
        new_imports = new["spring"]["config"].pop("import")
        if added_canary:
            new_imports = new_imports[:-1]
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


def source_literals(source):
    """只分离注释和字符串，不执行 TS/Java；供下面的有限路径语法使用。"""
    assert "FLEET_LITERAL_" not in source, "源码使用了校验器保留标识"
    masked, literals, index = [], {}, 0
    while index < len(source):
        if source.startswith("//", index):
            end = source.find("\n", index)
            index = len(source) if end < 0 else end
        elif source.startswith("/*", index):
            end = source.find("*/", index + 2)
            assert end >= 0, "源码注释未闭合"
            masked.append(" "); index = end + 2
        elif source[index] in "'\"`":
            quote, start = source[index], index + 1
            index = start
            while index < len(source) and source[index] != quote:
                index += 2 if source[index] == "\\" else 1
            assert index < len(source), "源码字符串未闭合"
            token = f"FLEET_LITERAL_{len(literals)}"
            literals[token] = (quote, source[start:index])
            masked.append(token); index += 1
        else:
            masked.append(source[index]); index += 1
    return "".join(masked), literals


def literal_route(token, literals, prefix=None):
    quote, value = literals[token]
    assert "\\" not in value, "未支持的路径转义表达式"
    if prefix is not None:
        marker = "${AI_SERVICE_PREFIX}"
        assert quote == "`" and value.startswith(marker), "AI 路径必须引用已核 AI_SERVICE_PREFIX"
        value = value[len(marker):]
    elif quote == "`":
        value = re.sub(r"\$\{encodeURIComponent\(\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*\)\}", r"{\1}", value)
    segment = r"(?:[A-Za-z0-9_.~-]+|\{[A-Za-z_$][A-Za-z0-9_$]*\})"
    assert re.fullmatch("/" + segment + "(?:/" + segment + ")*", value), "未支持的路径或动态参数表达式"
    assert not any(part in (".", "..") for part in value.split("/")), "路径不能包含跳转区段"
    return (prefix or "") + value


def ai_split_paths(source, module, prefix):
    """限定两种现有 adapter：前缀模板，或固定 root + storageRequest 相对路径。"""
    masked, literals = source_literals(source)
    imported = re.search(r"import\s*\{\s*AI_SERVICE_PREFIX\s*\}\s*from\s*(FLEET_LITERAL_\d+)", masked)
    assert imported and literals[imported[1]][1] == "@/constants/auth", "AI 模块前缀导入无法核对"
    assert not re.search(r"\bfetch\s*\(", masked), "未支持的网络表达式，不能只校验部分路径"
    exports = len(re.findall(r"\bexport\s+(?:async\s+)?(?:const|function)\s+", masked))

    def calls(name):
        pattern = r"\b" + name + r"\s*(?:<[^(){};]+>)?\s*\("
        return [item for item in re.finditer(pattern, masked)
                if not re.search(r"\bfunction\s*$", masked[:item.start()])]

    def argument(call):
        match = re.match(r"\s*(FLEET_LITERAL_\d+)\s*,", masked[call.end():])
        assert match, "请求路径必须是已支持的字符串表达式"
        return match[1]

    requests = calls("request")
    if module == "controlPlane.ts":
        assert requests and len(requests) == exports, "模型目录存在未解析的请求导出"
        return {literal_route(argument(call), literals, prefix) for call in requests}
    assert module == "storage.ts", "未支持的 AI 分模块"
    roots = list(re.finditer(r"\bconst\s+root\s*=\s*(FLEET_LITERAL_\d+)\s*;", masked))
    assert len(roots) == 1, "存储 root 必须是唯一固定前缀模板"
    root = literal_route(roots[0][1], literals, prefix)
    assert len(requests) == 1 and literals[argument(requests[0])] == ("`", "${root}${path}"), "存储请求必须按 root 与 path 组合"
    endpoints = calls("storageRequest")
    assert endpoints and len(endpoints) == exports, "存储模块存在未解析的请求导出"
    return {root + literal_route(argument(call), literals) for call in endpoints}


def check_controller_endpoint(path, controllers):
    """核对新模块的当前 Java 注解路径；仍不代表目标环境已部署或请求成功。"""
    normalize = lambda value: re.sub(r"\{[^/{}]+\}", "{}", value)
    endpoints = set()
    for base, source in controllers:
        if path != base and not path.startswith(base.rstrip("/") + "/"):
            continue
        masked, literals = source_literals(source)
        for mapping in re.finditer(r"@(?:Get|Post|Put|Delete|Patch)Mapping\b", masked):
            rest = masked[mapping.end():].lstrip()
            suffix = ""
            if rest.startswith("("):
                end = rest.find(")")
                assert end >= 0, "控制器路径注解未闭合"
                argument = rest[1:end].strip()
                if argument:
                    path_token, attributes = None, set()
                    for part in argument.split(","):
                        part = part.strip()
                        if re.fullmatch(r"FLEET_LITERAL_\d+", part):
                            name, value = "path", part
                        else:
                            attribute = re.fullmatch(r"(path|value|consumes|produces)\s*=\s*(.+)", part)
                            assert attribute, "未支持的控制器路径注解，不能猜测路径"
                            name, value = attribute.groups()
                            name = "path" if name == "value" else name
                        assert name not in attributes, "控制器路径注解存在重复字段"
                        attributes.add(name)
                        if name == "path":
                            assert re.fullmatch(r"FLEET_LITERAL_\d+", value), "控制器路径必须是明确字符串"
                            path_token = value
                        else:
                            assert re.fullmatch(r"(?:MediaType\.[A-Z_]+|FLEET_LITERAL_\d+)", value), "未支持的控制器媒体类型声明"
                    if path_token:
                        suffix = literal_route(path_token, literals)
            endpoints.add(normalize(base.rstrip("/") + suffix))
    assert normalize(path) in endpoints, "新增前端路径没有对应的当前控制器注解"


def check_frontend_routes(root, routes):
    constants = (root / "admin-web/src/constants/auth.ts").read_text()
    modules = {"auth": ("USER", "user"), "message": ("MESSAGE", "message"),
               "ai": ("AI", "ai"), "rag": ("RAG", "rag")}
    checked = 0
    for module, (constant, service) in modules.items():
        prefix = re.search(r"export const " + constant + r"_SERVICE_PREFIX = ['\"]([^'\"]*)['\"]", constants)
        assert prefix, constant
        roots, controllers = set(), []
        for file in (root / service / "src/main/java").rglob("*.java"):
            source = file.read_text()
            if "@RestController" in source:
                match = re.search(r'@RequestMapping\(\s*(?:(?:value|path)\s*=\s*)?"([^"\n]+)"', source)
                if match:
                    roots.add(match[1])
                    controllers.append((match[1], source))
        source = (root / f"admin-web/src/services/{module}/index.ts").read_text()
        paths = {match[1] for match in re.findall(r"(['\"`])(/(?:auth|api|messages)/[^'\"`\n]*)\1", source)}
        assert paths and roots, module
        for path in paths:
            check_route(prefix[1] + path, service, roots, routes)
            checked += 1
        if module == "ai":
            for name in ("controlPlane.ts", "storage.ts"):
                file = root / "admin-web/src/services/ai" / name
                assert file.is_file(), "缺少需要明确校验的 AI 分模块"
                for path in ai_split_paths(file.read_text(), name, prefix[1]):
                    check_route(path, service, roots, routes)
                    _, downstream = route_destination(path, routes)
                    check_controller_endpoint(downstream, controllers)
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
            documents = yaml_documents(text)
            check_local_canary_profile(service, environment, documents)
            value = documents[0]
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
    check_local_canary_template((CONFIG / LOCAL_CANARY_DATA_ID).read_text())
    check_catalog({file.name: file.read_text() for file in CONFIG.iterdir()},
                  json.loads((CONFIG.parent / "catalog.json").read_text()),
                  json.loads((CONFIG.parent / "retired-dataids.json").read_text()), consumers)
    check_model_credential_scope({file.name: file.read_text() for file in CONFIG.iterdir()},
                                 json.loads((CONFIG.parent / "catalog.json").read_text()),
                                 json.loads((CONFIG.parent / "retired-dataids.json").read_text()))
    gateway = yaml_json((CONFIG / "gateway-spring.yaml").read_text())
    routes = gateway["spring"]["cloud"]["gateway"]["server"]["webflux"]["routes"]
    frontend_paths = check_frontend_routes(ROOT, routes)
    check_storage_credential_scope({file.name: file.read_text() for file in CONFIG.iterdir()},
                                   json.loads((CONFIG.parent / "catalog.json").read_text()),
                                   json.loads((CONFIG.parent / "retired-dataids.json").read_text()))
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
          f"{frontend_paths} 前端路径（含 AI 分模块，仅静态校验）；隔离、路由和业务种子门禁通过")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, help="Git 外的整理前模板/profile JSON 快照")
    args = parser.parse_args()
    check()
    if args.baseline:
        check_consolidation_baseline(args.baseline)
