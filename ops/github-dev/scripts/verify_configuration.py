#!/usr/bin/env python3
"""检查 GitHub 环境隔离边界、配置覆盖和代码所需数据表。只读，不连接远端。"""
import re
import argparse
import json
import subprocess
import sys
from pathlib import Path
from migration_inventory import SERVICES as DATABASE_SERVICES, migration_inventory
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
        # 各服务的资源服务器向 user 取业务上下文（第5阶段起替代旧的令牌校验 RPC）。
        "[com.kellen.security.oauth2.SecurityContextRpcService]": ["ai", "ai-agent", "ai-memory", "mcp", "message", "rag", "report"],
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
            "instance": {"lane": "canary"},
            "canary": {"enabled": True, "service": "${spring.application.name}",
                       "http-tag": "${local-canary.tag}",
                       "signing-key": "${GITHUB_PERSONAL_CANARY_SIGNING_KEY}"},
        }},
    }, "个人灰度模板含未核准的共享注册、鉴权或签名设置"
    # Python 比较会把数字 1 当作 True，另外检查布尔类型。
    assert value["local-canary"]["ready"] is True
    assert value["traffic"]["governance"]["canary"]["enabled"] is True


# 第5阶段退役的键：认证改由 Keycloak 资源服务器、客户端凭证与令牌交换负责后，下列键（含其下级键）已无读取方，模板不得再出现。
RETIRED_KEYS = {
    "security-auth.yaml": ("security.auth",),
    "ai-spring.yaml": ("ai.agent-client.auth",),
    "ai-agent-spring.yaml": ("ai.agent.security",),
    "mcp-spring.yaml": ("mcp.oauth",),
    "rag-spring.yaml": ("rag.engine.api-key",),
    "user-spring.yaml": ("oauth",),
}


# 服务连 Nacos 的账号只引用私有环境、不带默认值：缺值时启动即失败，不能静默回落到默认账号（默认值 nacos 即管理员，CWE-1392）。
NACOS_CREDENTIALS = {"nacos-username": "${GITHUB_NACOS_USERNAME}", "nacos-password": "${GITHUB_NACOS_PASSWORD}"}


def check_nacos_credentials(value, file):
    custom = value["custom"]
    for key, expected in NACOS_CREDENTIALS.items():
        assert custom.get(key) == expected, f"{file}：custom.{key} 必须写成 {expected}，不带默认值"
    nacos = value["spring"]["cloud"]["nacos"]
    for kind in ("config", "discovery"):
        assert nacos[kind].get("username") == "${custom.nacos-username}", f"{file}：spring.cloud.nacos.{kind}.username"
        assert nacos[kind].get("password") == "${custom.nacos-password}", f"{file}：spring.cloud.nacos.{kind}.password"


def check_resource_server_audiences(config):
    # 配了签发方（资源服务器）的模板必须显式写受众：漏写时 utils 2.0.6 起回落到 github-api，但不能依赖缺省值。
    missing = []
    for data_id, text in sorted(config.items()):
        document = yaml_json(text)
        jwt = document if isinstance(document, dict) else {}
        for part in ("spring", "security", "oauth2", "resourceserver", "jwt"):
            jwt = jwt.get(part) if isinstance(jwt, dict) else None
        if isinstance(jwt, dict) and jwt.get("issuer-uri") and not jwt.get("audiences"):
            missing.append(data_id)
    assert not missing, f"配了 issuer-uri 的模板没有写受众 audiences：{', '.join(missing)}"


def check_retired_keys(config):
    found = []
    for data_id, paths in RETIRED_KEYS.items():
        document = yaml_json(config[data_id])
        for path in paths:
            node = document
            for part in path.split("."):
                node = node.get(part) if isinstance(node, dict) else None
            if node is not None:
                found.append(f"{data_id}:{path}")
    assert not found, f"模板仍含第5阶段退役的键：{', '.join(found)}"


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
    """历史共享模型项已退役，不得重新发布；Google 参数由 Agent 显式持有。"""
    legacy = "ai-model-secrets.yaml"
    assert legacy not in config and legacy not in {item["dataId"] for item in catalog["entries"]}, "历史共享模型不得重新进入活跃模板或 catalog"
    assert legacy in {item["dataId"] for item in retired["retired"] if item["replacement"] is None}, "历史共享模型须登记为无替代的退役项"
    agent = yaml_json(config["ai-agent-spring.yaml"])
    google = agent["spring"]["ai"]["google"]["genai"]
    assert google["api-key"] == "${GITHUB_GOOGLE_API_KEY}", "Agent Google 凭据须由本服务私有占位符提供"
    assert google["chat"]["options"]["model"] == "${GITHUB_GOOGLE_CHAT_MODEL:gemini-3.1-flash-lite}", "Agent Google 模型占位符或默认值改变"
    assert agent["ai"]["agent"]["google"]["base-url"] == "${GITHUB_GOOGLE_BASE_URL:}", "Agent Google 原生基址必须可选且默认空值"


def check_storage_credential_scope(config, catalog, retired):
    active = {entry["dataId"] for entry in catalog["entries"]}
    removed = {entry["dataId"] for entry in retired["retired"]}
    assert "storage.yaml" not in config and "storage.yaml" not in active, "历史存储连接不得重新进入活动 Nacos"
    assert "storage.yaml" in removed, "历史存储配置须登记为退役项"
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


def literal_route(token, literals):
    """Java 控制器注解里的路径字面量；只认固定区段与 {名字} 参数。"""
    quote, value = literals[token]
    assert "\\" not in value, "未支持的路径转义表达式"
    assert quote != "`", "控制器路径不支持模板字面量"
    segment = r"(?:[A-Za-z0-9_.~-]+|\{[A-Za-z_$][A-Za-z0-9_$]*\})"
    assert re.fullmatch("/" + segment + "(?:/" + segment + ")*", value), "未支持的路径或动态参数表达式"
    assert not any(part in (".", "..") for part in value.split("/")), "路径不能包含跳转区段"
    return value


def top_level_parts(argument):
    """注解参数按顶层逗号拆分，数组 {...} 里的逗号不拆。"""
    parts, depth, start = [], 0, 0
    for position, character in enumerate(argument):
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            assert depth >= 0, "控制器注解数组未闭合"
        elif character == "," and depth == 0:
            parts.append(argument[start:position].strip())
            start = position + 1
    assert depth == 0, "控制器注解数组未闭合"
    parts.append(argument[start:].strip())
    return [part for part in parts if part]


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
                    for part in top_level_parts(argument):
                        if re.fullmatch(r"FLEET_LITERAL_\d+", part):
                            name, value = "path", part
                        else:
                            attribute = re.fullmatch(r"(path|value|consumes|produces|params|headers)\s*=\s*(.+)", part, re.S)
                            assert attribute, "未支持的控制器路径注解，不能猜测路径"
                            name, value = attribute.groups()
                            name = "path" if name == "value" else name
                        assert name not in attributes, "控制器路径注解存在重复字段"
                        attributes.add(name)
                        if name == "path":
                            assert re.fullmatch(r"FLEET_LITERAL_\d+", value), "控制器路径必须是明确字符串"
                            path_token = value
                        elif name in ("params", "headers"):
                            # 请求参数与请求头条件只决定同一路径上选哪个方法，不改变路径；值只认字符串或字符串数组。
                            assert re.fullmatch(r"(?:FLEET_LITERAL_\d+|\{\s*FLEET_LITERAL_\d+(?:\s*,\s*FLEET_LITERAL_\d+)*\s*\})", value), \
                                "未支持的控制器请求条件声明"
                        else:
                            media = r"(?:MediaType\.[A-Z_]+|FLEET_LITERAL_\d+)"
                            assert re.fullmatch(media + r"|\{\s*" + media + r"(?:\s*,\s*" + media + r")*\s*\}", value), "未支持的控制器媒体类型声明"
                    if path_token:
                        suffix = literal_route(path_token, literals)
            endpoints.add(normalize(base.rstrip("/") + suffix))
    assert normalize(path) in endpoints, "新增前端路径没有对应的当前控制器注解"


# admin-web 1.2.5 起的多应用结构：各应用的接口在 apps/<应用>/src/services，会话接口在 packages/auth。
FRONTEND_REQUEST_SOURCES = ("apps/system/src/services", "apps/message/src/services", "apps/ai/src/services",
                            "apps/rag/src/services", "packages/auth/src/endpoints.ts")
# 路径前缀归属的服务：前端约定（即 1.2.4 的 USER/MESSAGE/AI/RAG_SERVICE_PREFIX），独立于网关路由表，长前缀在前。
FRONTEND_PATH_SERVICES = (("/api/rag/", "rag"), ("/api/ai/", "ai"), ("/message/", "message"), ("/auth/", "user"))
# 网关 BFF 自己处理、不转发到后端服务的路径。
GATEWAY_LOCAL_PATHS = frozenset({"/logout"})
# axios 实例（client）与 @admin-web/api 的 createServiceClient（api）上的请求方法。
REQUEST_METHODS = frozenset({"get", "post", "put", "patch", "delete", "request", "getPage", "getList", "remove"})
IDENTIFIER = r"[A-Za-z_$][A-Za-z0-9_$]*"


def matching(masked, index, opening, closing):
    """index 指向 opening；返回与之配对的 closing 的位置（字符串已遮住，不会误配）。"""
    depth = 0
    for position in range(index, len(masked)):
        if masked[position] == opening:
            depth += 1
        elif masked[position] == closing:
            depth -= 1
            if depth == 0:
                return position
    raise AssertionError("括号未闭合")


def after_generic(masked, index):
    """跳过紧跟的泛型参数 <...>（可嵌套），返回其后第一个非空白字符的位置。"""
    while index < len(masked) and masked[index].isspace():
        index += 1
    if index < len(masked) and masked[index] == "<":
        depth, position = 0, index
        while position < len(masked):
            if masked.startswith("=>", position):
                position += 2
                continue
            if masked[position] == "<":
                depth += 1
            elif masked[position] == ">":
                depth -= 1
                if depth == 0:
                    index = position + 1
                    break
            position += 1
        else:
            raise AssertionError("泛型参数未闭合")
        while index < len(masked) and masked[index].isspace():
            index += 1
    return index


def first_argument(masked, opening):
    """opening 指向调用的 '('；返回第一个参数的源码（顶层逗号或右括号之前）。"""
    depth = 0
    for position in range(opening + 1, len(masked)):
        character = masked[position]
        if character in "([{":
            depth += 1
        elif character in ")]}":
            if depth == 0:
                return masked[opening + 1:position].strip()
            depth -= 1
        elif character == "," and depth == 0:
            return masked[opening + 1:position].strip()
    raise AssertionError("调用括号未闭合")


def frontend_route(token, literals, constants):
    """前端路径字面量：模板里的路径常量照值代入，参数（标识符、segment()/encodeURIComponent() 包一个标识符）记为 {}。"""
    quote, value = literals[token]
    assert "\\" not in value, "未支持的路径转义表达式"
    if quote == "`":
        def parameter(match):
            expression = match[1].strip()
            if expression in constants:
                return constants[expression]
            chain = IDENTIFIER + r"(?:\." + IDENTIFIER + ")*"
            assert re.fullmatch(r"(?:(?:segment|encodeURIComponent)\(\s*" + chain + r"\s*\)|" + chain + ")", expression), \
                "未支持的路径参数表达式：" + expression
            return "{}"
        value = re.sub(r"\$\{([^{}]*)\}", parameter, value)
    segment = r"(?:[A-Za-z0-9_.~-]+|\{\})"
    assert re.fullmatch("/" + segment + "(?:/" + segment + ")*", value), "未支持的路径表达式：" + value
    assert not any(part in (".", "..") for part in value.split("/")), "路径不能包含跳转区段"
    return value


def frontend_requests(source, name):
    """返回一个前端文件里每个请求的网关路径；遇到解析不了的网络调用就失败，不报告部分覆盖。"""
    masked, literals = source_literals(source)
    assert not re.search(r"\b(?:fetch|XMLHttpRequest|EventSource|WebSocket)\b|\baxios\s*\.", masked), \
        name + "：未支持的网络表达式，不能只校验部分路径"
    constants = {}
    for match in re.finditer(r"\bconst\s+(" + IDENTIFIER + r")\s*=\s*(FLEET_LITERAL_\d+)\s*;", masked):
        _, value = literals[match[2]]
        if value.startswith("/"):
            assert "${" not in value, name + "：路径常量不能含表达式"
            constants[match[1]] = value

    def path_of(argument):
        if re.fullmatch(r"FLEET_LITERAL_\d+", argument):
            return frontend_route(argument, literals, constants)
        assert argument in constants, name + "：请求路径必须是字面量或模块里的路径常量：" + argument
        return constants[argument]

    def url_of(argument):
        assert argument.startswith("{") and argument.endswith("}"), name + "：请求配置必须是对象字面量"
        urls = re.findall(r"\burl\s*:\s*([^,}]+)", argument)
        assert len(urls) == 1 and re.fullmatch(r"FLEET_LITERAL_\d+", urls[0].strip()), name + "：请求配置的 url 必须是唯一的路径字面量"
        return frontend_route(urls[0].strip(), literals, constants)

    # 本地辅助函数：第一个形参是请求配置，函数体里用 client.request 发出（原样，或把 url 拼在路径常量后）。
    helpers, spans = {}, []
    for match in re.finditer(r"\bfunction\s+(" + IDENTIFIER + r")\s*(?:<[^()]*>)?\s*\(\s*(" + IDENTIFIER + r")\s*:", masked):
        opening = masked.index("(", match.start(1))
        body = masked.index("{", matching(masked, opening, "(", ")"))
        end = matching(masked, body, "{", "}")
        calls = list(re.finditer(r"\bclient\s*\.\s*request\b", masked[body:end]))
        if not calls:
            continue
        assert len(calls) == 1, name + "：辅助函数只能发出一个请求"
        position = after_generic(masked, body + calls[0].end())
        assert masked[position] == "(", name + "：辅助函数的请求无法解析"
        argument, config = first_argument(masked, position), match[2]
        if argument == config:
            base = ""
        else:
            assert argument.startswith("{") and re.search(r"\.\.\.\s*" + config + r"\b", argument), name + "：辅助函数必须原样转发请求配置"
            urls = re.findall(r"\burl\s*:\s*(FLEET_LITERAL_\d+)", argument)
            assert len(urls) == 1, name + "：辅助函数的 url 必须是唯一的模板"
            joined = re.fullmatch(r"\$\{(" + IDENTIFIER + r")\}\$\{" + config + r"\.url\}", literals[urls[0]][1])
            assert literals[urls[0]][0] == "`" and joined and joined[1] in constants, name + "：辅助函数的 url 必须是路径常量加 config.url"
            base = constants[joined[1]]
        helpers[match[1]] = base
        spans.append((body, end))

    paths = []
    for match in re.finditer(r"\b(client|api)\s*\.\s*(" + IDENTIFIER + r")", masked):
        if any(start <= match.start() <= end for start, end in spans):
            continue
        position = after_generic(masked, match.end())
        if position >= len(masked) or masked[position] != "(":
            continue
        assert match[2] in REQUEST_METHODS, name + "：未支持的请求方法 " + match[1] + "." + match[2]
        argument = first_argument(masked, position)
        paths.append(url_of(argument) if match[2] == "request" else path_of(argument))
    for helper, base in helpers.items():
        for match in re.finditer(r"\b" + re.escape(helper) + r"\b", masked):
            if re.search(r"\bfunction\s*$", masked[:match.start()]):
                continue
            position = after_generic(masked, match.end())
            if position < len(masked) and masked[position] == "(":
                paths.append(base + url_of(first_argument(masked, position)))
    return paths


def frontend_service(path):
    for prefix, service in FRONTEND_PATH_SERVICES:
        if path.startswith(prefix):
            return service
    raise AssertionError("前端路径不属于任何已知服务：" + path)


def check_frontend_routes(root, routes):
    """前端每个请求都要经网关路由到前缀所属的服务，且落在该服务某个控制器的注解路径上；返回核对的不同路径数。"""
    web = Path(root) / "admin-web"
    controllers = {}

    def service_controllers(service):
        if service not in controllers:
            found = []
            for file in sorted((Path(root) / service / "src/main/java").rglob("*.java")):
                source = file.read_text()
                if "@RestController" in source:
                    match = re.search(r'@RequestMapping\(\s*(?:(?:value|path)\s*=\s*)?"([^"\n]+)"', source)
                    if match:
                        found.append((match[1], source))
            assert found, "服务没有带根路径的控制器：" + service
            controllers[service] = found
        return controllers[service]

    checked = set()
    for relative in FRONTEND_REQUEST_SOURCES:
        location = web / relative
        files = [location] if location.suffix == ".ts" else sorted(location.glob("*.ts"))
        paths = []
        for file in files:
            if file.is_file():
                paths += frontend_requests(file.read_text(), str(file.relative_to(web)))
        assert paths, "前端结构变了，没有找到请求：admin-web/" + relative
        for path in paths:
            if path in GATEWAY_LOCAL_PATHS:
                continue
            service = frontend_service(path)
            found = service_controllers(service)
            check_route(path, service, {base for base, _ in found}, routes)
            _, downstream = route_destination(path, routes)
            check_controller_endpoint(downstream, found)
            checked.add(path)
    return len(checked)


def check_entity_tables(root, inventory):
    """每个 @TableName 引用的表都必须由本服务的 Flyway 迁移建出；缺了就报出服务与表名。"""
    for service, tables in inventory.items():
        used = set()
        for file in (Path(root) / service / "src/main/java").rglob("*.java"):
            used.update(re.findall(r'@TableName\([^\n]*?"([^"\n]+)"', file.read_text()))
        assert used <= tables, (service, sorted(used - tables))


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
            check_nacos_credentials(value, file)
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
    check_retired_keys({file.name: file.read_text() for file in CONFIG.iterdir()})
    check_resource_server_audiences({file.name: file.read_text() for file in CONFIG.iterdir()})
    auth = yaml_json((CONFIG / "security-auth.yaml").read_text())
    assert auth["tenant"]["ignore-without-tenant"] is False
    inventory = migration_inventory(ROOT)
    sql = "\n".join(file.read_text() for service in DATABASE_SERVICES
                    for file in (ROOT / service / "src/main/resources/db/migration").glob("V*__*.sql"))
    assert not re.search(r"INSERT(?: IGNORE)? INTO auth_user\b", sql, re.I), "迁移不得预置人员账号"
    assert not re.search(r"meilianshuke\.com|hz2y\.com", sql)
    check_entity_tables(ROOT, inventory)
    print(f"PASS: {len(SERVICES)} 服务 / 27 profile / {len(imported)} dataId / "
          f"{sum(len(tables) for tables in inventory.values())} 张表（Flyway 迁移后）/ "
          f"{frontend_paths} 前端路径（apps 与 packages/auth，仅静态校验）；隔离、路由和业务种子门禁通过")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, help="Git 外的整理前模板/profile JSON 快照")
    args = parser.parse_args()
    check()
    if args.baseline:
        check_consolidation_baseline(args.baseline)
