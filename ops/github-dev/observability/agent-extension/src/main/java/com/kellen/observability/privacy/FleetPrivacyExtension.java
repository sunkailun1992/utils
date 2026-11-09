package com.kellen.observability.privacy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 固定 agent 2.33.1 的出站隐私边界，由 agent classloader 加载，不属于应用 SDK。
 * SDK 入队前禁止 events；实际 exporter 之前只留有限元数据，不保留动态 URL、SQL、异常或正文。
 */
public final class FleetPrivacyExtension implements AutoConfigurationCustomizerProvider {
    private static final Set<String> RESOURCE_KEYS = Set.of(
            "service.name", "service.version", "deployment.environment.name");
    private static final Set<String> BUSINESS_NAMES = Set.of(
            "ai.dialogue.stream", "ai.agent.execution", "ai.model.call", "memory.retrieve", "rag.prefetch",
            "memory.context", "rag.retrieval", "rag.retrieve", "mcp.protocol");
    private static final Map<String, Set<String>> ENUMS = Map.of(
            "http.request.method", Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "_OTHER"),
            "rpc.system", Set.of("dubbo", "apache_dubbo", "grpc", "java_rmi", "jsonrpc"),
            "rpc.system.name", Set.of("dubbo", "apache_dubbo", "grpc", "java_rmi", "jsonrpc"),
            "db.system.name", Set.of("mysql", "postgresql", "redis", "elasticsearch", "h2"),
            "rag.stage", Set.of("parse", "retrieval", "embedding", "recall", "graph_expand", "rerank", "graph_submit",
                    "graph_query", "graph_build", "worker_run", "source_recheck", "publish", "upstream"),
            "rag.mode", Set.of("dense", "hybrid", "graph"),
            "rag.result", Set.of("success", "failed", "cancelled", "timeout", "degraded", "empty", "client_error"),
            "business.outcome", Set.of("success", "error", "cancelled", "matched", "empty", "degraded", "unavailable",
                    "failed", "timeout", "protocol_error", "business_error", "disabled", "unmatched", "rejected", "notification"),
            "outcome", Set.of("success", "failure", "cancelled"),
            "operation", Set.of("patient_analysis", "questionnaire_reply", "questionnaire_interpret"));
    private static final Map<String, Set<String>> AI_ENUMS = Map.of(
            "gen_ai.operation.name", Set.of("chat", "generate_content", "embeddings", "retrieval", "invoke_agent", "execute_tool"),
            "gen_ai.provider.name", Set.of("gcp.gemini", "google", "deepseek", "qwen", "alibaba.dashscope", "openai"),
            "fleet.ai.prompt.source", Set.of("db", "database", "nacos", "caller", "local", "fallback", "override", "unknown"),
            "fleet.ai.reasoning.kind", Set.of("none", "summary", "provider_output"));
    private static final Set<String> AI_IDENTIFIERS = Set.of(
            "gen_ai.request.model", "gen_ai.response.model", "fleet.ai.prompt.key", "fleet.ai.prompt.version");
    private static final Set<String> AI_COUNTS = Set.of(
            "gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens", "gen_ai.usage.reasoning.output_tokens",
            "gen_ai.usage.cache_read.input_tokens", "gen_ai.usage.cache_creation.input_tokens",
            "fleet.ai.input.characters", "fleet.ai.output.characters", "fleet.ai.input.message_count",
            "fleet.ai.input.document_count");
    private static final Set<String> FLAGS = Set.of(
            "rag.degraded", "rag.graph_augmented", "rag.graph_degraded", "fleet.ai.usage.available", "fleet.ai.usage.final",
            "fleet.ai.reasoning.present", "fleet.ai.reasoning.summary_present");
    private static final Set<String> FINISH_REASONS = Set.of(
            "stop", "length", "tool_calls", "function_call", "content_filter", "error", "cancelled", "unknown",
            "max_tokens", "safety", "recitation", "other",
            "STOP", "MAX_TOKENS", "SAFETY", "RECITATION", "OTHER", "FINISH_REASON_UNSPECIFIED");

    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        // properties customizer 覆盖 env/system properties，不能靠调用方重新开启原始异常 events。
        customizer.addPropertiesCustomizer(config -> Map.of(
                "otel.span.event.count.limit", "0", "otel.metrics.exporter", "none", "otel.logs.exporter", "none"));
        customizer.addSpanExporterCustomizer((delegate, config) -> new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                return delegate.export(spans.stream().map(SafeSpan::new).map(SpanData.class::cast).toList());
            }
            @Override
            public CompletableResultCode flush() { return delegate.flush(); }
            @Override
            public CompletableResultCode shutdown() { return delegate.shutdown(); }
        });
    }

    private static Attributes safeAttributes(Attributes source) {
        var safe = Attributes.builder();
        source.forEach((key, value) -> {
            Set<String> values = ENUMS.get(key.getKey());
            if (values == null) values = AI_ENUMS.get(key.getKey());
            if (values != null && value instanceof String string && values.contains(string)) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            } else if ("http.response.status_code".equals(key.getKey()) && value instanceof Long number
                    && number >= 100 && number <= 599) {
                safe.put(AttributeKey.longKey(key.getKey()), number);
            } else if ("rag.hit_count".equals(key.getKey()) && value instanceof Long number && number >= 0) {
                safe.put(AttributeKey.longKey(key.getKey()), number);
            } else if (AI_COUNTS.contains(key.getKey()) && value instanceof Long number && number >= 0 && number <= 1_000_000_000L) {
                safe.put(AttributeKey.longKey(key.getKey()), number);
            } else if (AI_IDENTIFIERS.contains(key.getKey()) && value instanceof String string
                    && string.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}") && !string.contains("://")) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            } else if (FLAGS.contains(key.getKey())
                    && value instanceof Boolean flag) {
                safe.put(AttributeKey.booleanKey(key.getKey()), flag);
            } else if ("gen_ai.response.finish_reasons".equals(key.getKey()) && value instanceof List<?> reasons
                    && !reasons.isEmpty() && reasons.size() <= 8 && reasons.stream().allMatch(FINISH_REASONS::contains)) {
                safe.put(AttributeKey.stringArrayKey(key.getKey()), reasons.stream().map(String.class::cast).toList());
            } else if ("rpc.service".equals(key.getKey()) && value instanceof String string
                    && string.startsWith("com.kellen.rpc.") && string.matches("[A-Za-z0-9_.$]{1,200}")) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            } else if ("rpc.method".equals(key.getKey()) && value instanceof String string
                    && string.length() <= 200
                    && string.matches("(?:com\\.kellen\\.rpc\\.[A-Za-z0-9_.$]+[/#.])?[a-z][A-Za-z0-9]{0,79}")) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            } else if ("http.route".equals(key.getKey()) && value instanceof String string
                    && (string.matches("/(?:api|actuator|auth|messages|message|user|report|ai-memory|rag|mcp)/.*") || string.equals("/mcp"))
                    && string.matches("/[A-Za-z0-9_/{\\}*.-]{0,199}")) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            } else if ("error.type".equals(key.getKey()) && value instanceof String string
                    && string.matches("(?:[a-z][A-Za-z0-9_]*\\.)+[A-Z][A-Za-z0-9_$]{0,79}")) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            }
        });
        return safe.build();
    }

    private static SpanContext identity(SpanContext context) {
        return SpanContext.create(context.getTraceId(), context.getSpanId(), context.getTraceFlags(), TraceState.getDefault());
    }

    /** exporter 只看到此视图；trace/span/parent IDs、时间与 status code 保留。 */
    private static final class SafeSpan extends DelegatingSpanData {
        private final Attributes attributes;
        private final Resource resource;
        SafeSpan(SpanData delegate) {
            super(delegate);
            attributes = safeAttributes(delegate.getAttributes());
            var metadata = Attributes.builder();
            delegate.getResource().getAttributes().forEach((key, value) -> {
                if (RESOURCE_KEYS.contains(key.getKey()) && value instanceof String string) {
                    metadata.put(AttributeKey.stringKey(key.getKey()), string);
                }
            });
            resource = Resource.create(metadata.build());
        }
        @Override
        public Attributes getAttributes() { return attributes; }
        @Override
        public Resource getResource() { return resource; }
        @Override
        public List<EventData> getEvents() { return List.of(); }
        @Override
        public int getTotalRecordedEvents() { return 0; }
        @Override
        public int getTotalAttributeCount() { return attributes.size(); }
        @Override
        public List<LinkData> getLinks() {
            return super.getLinks().stream().map(link -> LinkData.create(identity(link.getSpanContext()))).toList();
        }
        @Override
        public StatusData getStatus() { return StatusData.create(super.getStatus().getStatusCode(), ""); }
        @Override
        public SpanContext getSpanContext() { return identity(super.getSpanContext()); }
        @Override
        public SpanContext getParentSpanContext() { return identity(super.getParentSpanContext()); }
        @Override
        public InstrumentationScopeInfo getInstrumentationScopeInfo() {
            return InstrumentationScopeInfo.create("fleet.privacy");
        }
        @Override
        public String getName() {
            // 自动 instrumentation 的名称也可能包含 SQL/路径；只有固定业务名称原样保留。
            return BUSINESS_NAMES.contains(super.getName()) ? super.getName()
                    : getKind().name().toLowerCase(Locale.ROOT) + " operation";
        }
    }
}
