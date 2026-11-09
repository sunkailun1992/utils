"""固定 Collector 0.164 的安全日志字段合同；生成片段供两道边界共用。"""
import json

STRING_LIMITS = {
    "event": 64, "summary": 512, "http_method": 16, "http_route": 200,
    "request_body": 8192, "response_body": 8192,
    "request_body_omitted": 32, "response_body_omitted": 32,
    "error_type": 120, "error_code": 80, "outcome": 32, "stage": 80,
    "trace_id": 32, "span_id": 16,
    "ai_operation": 80, "ai_provider": 80, "ai_model": 128, "ai_response_model": 128,
    "ai_prompt_key": 128, "ai_prompt_version": 128, "ai_prompt_source": 32,
    "ai_reasoning_kind": 32, "ai_finish_reason": 32,
    "ai_input_summary": 512, "ai_output_summary": 512,
}
COUNTS = ("request_bytes", "response_bytes", "ai_input_tokens", "ai_output_tokens",
          "ai_reasoning_tokens", "ai_input_characters", "ai_output_characters",
          "ai_message_count", "ai_document_count")
FLAGS = ("request_body_complete", "response_body_complete", "ai_usage_available", "ai_reasoning_present")
LOG_FIELDS = tuple(STRING_LIMITS) + COUNTS + FLAGS + ("duration_ms", "http_status")
RESOURCE_FIELDS = ("service.name", "service.version", "deployment.environment.name")


def log_statements(include_resource=False):
    """先丢未知字段，再类型/范围校验；不把字符串数字或未知类型强转成正文。"""
    result = ['keep_keys(attributes, ' + json.dumps(list(RESOURCE_FIELDS if include_resource else ()) + list(LOG_FIELDS)) + ')']
    for key, limit in STRING_LIMITS.items():
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsString(attributes["{key}"])',
                   f'delete_key(attributes, "{key}") where IsString(attributes["{key}"]) and Len(attributes["{key}"]) > {limit}']
    for key in COUNTS + ("http_status",):
        # filelog的JSON number为Double；仅精确整数且在JSON安全整数范围才转Long，字符串不转换。
        result += [f'set(attributes["{key}"], Int(attributes["{key}"])) where IsDouble(attributes["{key}"]) and attributes["{key}"] >= 0 and attributes["{key}"] <= 9007199254740991 and attributes["{key}"] == Double(Int(attributes["{key}"]))',
                   f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsInt(attributes["{key}"])']
        lower, upper = (100, 599) if key == "http_status" else (0, 9223372036854775807)
        result += [f'delete_key(attributes, "{key}") where IsInt(attributes["{key}"]) and (attributes["{key}"] < {lower} or attributes["{key}"] > {upper})']
    for key in FLAGS:
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsBool(attributes["{key}"])']
    result += ['delete_key(attributes, "duration_ms") where attributes["duration_ms"] != nil and not IsInt(attributes["duration_ms"]) and not IsDouble(attributes["duration_ms"])',
               'delete_key(attributes, "duration_ms") where attributes["duration_ms"] != nil and attributes["duration_ms"] < 0']
    return result

# 与应用SDK/agent薄扩展同步；只允许GenAI标准元数据，不允许prompt/output/URL。
SPAN_ENUMS = {
    "http.request.method": ("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "OTHER", "_OTHER"),
    "db.system.name": ("mysql", "postgresql", "redis", "elasticsearch", "h2"),
    "rag.stage": ("parse", "retrieval", "embedding", "recall", "graph_expand", "rerank", "graph_submit", "graph_query", "graph_build", "worker_run", "source_recheck", "publish", "upstream"),
    "rag.mode": ("dense", "hybrid", "graph"),
    "rag.result": ("success", "failed", "cancelled", "timeout", "degraded", "empty", "client_error"),
    "business.outcome": ("success", "error", "cancelled", "matched", "empty", "degraded", "unavailable", "failed", "timeout", "protocol_error", "business_error", "disabled", "unmatched", "rejected", "notification"),
    "outcome": ("success", "failure", "cancelled", "timeout", "rejected"),
    "operation": ("patient_analysis", "questionnaire_reply", "questionnaire_interpret"),
    "gen_ai.operation.name": ("chat", "generate_content", "embeddings", "retrieval", "invoke_agent", "execute_tool"),
    "gen_ai.provider.name": ("gcp.gemini", "google", "deepseek", "qwen", "alibaba.dashscope", "openai"),
    "fleet.ai.prompt.source": ("db", "database", "nacos", "caller", "local", "fallback", "override", "unknown"),
    "fleet.ai.reasoning.kind": ("none", "summary", "provider_output"),
    "rpc.system": ("dubbo", "apache_dubbo", "grpc", "java_rmi", "jsonrpc"),
    "rpc.system.name": ("dubbo", "apache_dubbo", "grpc", "java_rmi", "jsonrpc"),
}
SPAN_IDENTIFIERS = ("gen_ai.request.model", "gen_ai.response.model", "fleet.ai.prompt.key", "fleet.ai.prompt.version")
SPAN_COUNTS = ("gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens", "gen_ai.usage.reasoning.output_tokens",
               "gen_ai.usage.cache_read.input_tokens", "gen_ai.usage.cache_creation.input_tokens",
               "fleet.ai.input.characters", "fleet.ai.output.characters", "fleet.ai.input.message_count", "fleet.ai.input.document_count")
SPAN_FLAGS = ("rag.degraded", "rag.graph_augmented", "rag.graph_degraded", "fleet.ai.usage.available", "fleet.ai.usage.final", "fleet.ai.reasoning.present", "fleet.ai.reasoning.summary_present")
SPAN_FIELDS = ("http.response.status_code", "http.route", "rpc.service", "rpc.method", "error.type", "rag.hit_count") + tuple(SPAN_ENUMS) + SPAN_IDENTIFIERS + SPAN_COUNTS + SPAN_FLAGS + ("gen_ai.response.finish_reasons",)


def span_statements():
    result = []
    for key, values in SPAN_ENUMS.items():
        invalid = ' and '.join(f'attributes["{key}"] != {json.dumps(v)}' for v in values)
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and ({invalid})']
    for key in SPAN_IDENTIFIERS:
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsString(attributes["{key}"])',
                   f'delete_key(attributes, "{key}") where IsString(attributes["{key}"]) and not IsMatch(attributes["{key}"], "^[A-Za-z0-9][A-Za-z0-9._:/-]{{0,127}}$")',
                   f'delete_key(attributes, "{key}") where IsString(attributes["{key}"]) and IsMatch(attributes["{key}"], "://")']
    for key in SPAN_COUNTS:
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsInt(attributes["{key}"])',
                   f'delete_key(attributes, "{key}") where IsInt(attributes["{key}"]) and (attributes["{key}"] < 0 or attributes["{key}"] > 1000000000)']
    for key in SPAN_FLAGS:
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsBool(attributes["{key}"])']
    for key, lower, upper in (("http.response.status_code", 100, 599), ("rag.hit_count", 0, 1000000000)):
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsInt(attributes["{key}"])',
                   f'delete_key(attributes, "{key}") where IsInt(attributes["{key}"]) and (attributes["{key}"] < {lower} or attributes["{key}"] > {upper})']
    patterns = {
        "rpc.service": r"^com[.]kellen[.]rpc[.][A-Za-z0-9_.$]{1,180}$",
        "rpc.method": r"^(com[.]kellen[.]rpc[.][A-Za-z0-9_.$]+[/#.])?[a-z][A-Za-z0-9]{0,79}$",
        "http.route": r"^(/(api|actuator|auth|messages|message|user|report|ai-memory|rag|mcp|v1)/[A-Za-z0-9_/{\}*.-]{0,180}|/health|/metrics|/mcp|other)$",
        "error.type": r"^([a-z][A-Za-z0-9_]*[.])+[A-Z][A-Za-z0-9_$]{0,79}$",
    }
    for key, pattern in patterns.items():
        result += [f'delete_key(attributes, "{key}") where attributes["{key}"] != nil and not IsString(attributes["{key}"])',
                   f'delete_key(attributes, "{key}") where IsString(attributes["{key}"]) and (Len(attributes["{key}"]) > 200 or not IsMatch(attributes["{key}"], ' + json.dumps(pattern) + '))']
    # 数组只由应用适配器+薄扩展的有限finish reason输出；中央对非法类型/超量失败关闭。
    result += ['delete_key(attributes, "gen_ai.response.finish_reasons") where attributes["gen_ai.response.finish_reasons"] != nil and not IsList(attributes["gen_ai.response.finish_reasons"])',
               'delete_key(attributes, "gen_ai.response.finish_reasons") where IsList(attributes["gen_ai.response.finish_reasons"]) and Len(attributes["gen_ai.response.finish_reasons"]) > 8']
    finish_pattern = '^\\["(stop|length|tool_calls|function_call|content_filter|error|cancelled|unknown|max_tokens|safety|recitation|other|STOP|MAX_TOKENS|SAFETY|RECITATION|OTHER|FINISH_REASON_UNSPECIFIED)"(,\\s*"(stop|length|tool_calls|function_call|content_filter|error|cancelled|unknown|max_tokens|safety|recitation|other|STOP|MAX_TOKENS|SAFETY|RECITATION|OTHER|FINISH_REASON_UNSPECIFIED)"){0,7}\\]$'
    result += ['delete_key(attributes, "gen_ai.response.finish_reasons") where IsList(attributes["gen_ai.response.finish_reasons"]) and not IsMatch(String(attributes["gen_ai.response.finish_reasons"]), ' + json.dumps(finish_pattern) + ')']
    return result
