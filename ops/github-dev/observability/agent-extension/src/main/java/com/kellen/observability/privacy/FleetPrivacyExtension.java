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
            "ai.dialogue.stream", "ai.agent.execution", "rag.retrieval", "rag.retrieve", "mcp.protocol");
    private static final Map<String, Set<String>> ENUMS = Map.of(
            "http.request.method", Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "_OTHER"),
            "rpc.system", Set.of("dubbo", "grpc", "java_rmi", "jsonrpc"),
            "db.system.name", Set.of("mysql", "postgresql", "redis", "elasticsearch", "h2"),
            "rag.stage", Set.of("parse", "retrieval", "embedding", "recall", "graph_expand", "rerank", "graph_submit",
                    "graph_query", "graph_build", "worker_run", "source_recheck", "publish", "upstream"),
            "rag.mode", Set.of("dense", "hybrid", "graph"),
            "rag.result", Set.of("success", "failed", "cancelled", "timeout", "degraded", "empty", "client_error"),
            "business.outcome", Set.of("success", "error", "cancelled", "matched", "empty", "degraded", "unavailable",
                    "failed", "timeout", "protocol_error", "business_error", "disabled", "unmatched", "rejected", "notification"),
            "outcome", Set.of("success", "failure", "cancelled"),
            "operation", Set.of("patient_analysis", "questionnaire_reply", "questionnaire_interpret"));

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
            if (values != null && value instanceof String string && values.contains(string)) {
                safe.put(AttributeKey.stringKey(key.getKey()), string);
            } else if ("http.response.status_code".equals(key.getKey()) && value instanceof Long number
                    && number >= 100 && number <= 599) {
                safe.put(AttributeKey.longKey(key.getKey()), number);
            } else if ("rag.hit_count".equals(key.getKey()) && value instanceof Long number && number >= 0) {
                safe.put(AttributeKey.longKey(key.getKey()), number);
            } else if (Set.of("rag.degraded", "rag.graph_augmented", "rag.graph_degraded").contains(key.getKey())
                    && value instanceof Boolean flag) {
                safe.put(AttributeKey.booleanKey(key.getKey()), flag);
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
