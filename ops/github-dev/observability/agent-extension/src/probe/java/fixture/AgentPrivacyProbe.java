package fixture;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** 合成应用仅依赖 API 1.59；真实 agent 提供桥接 SDK 和 JDK HttpClient 自动埋点。 */
public final class AgentPrivacyProbe {
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("io.opentelemetry.sdk.OpenTelemetrySdk");
            throw new AssertionError("SDK leaked onto application classpath");
        } catch (ClassNotFoundException expected) {
            // 应用必须保持 API-only。
        }
        var tracer = GlobalOpenTelemetry.getTracer("fleet.business");
        Span span = tracer.spanBuilder("rag.retrieve")
                .addLink(io.opentelemetry.api.trace.SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef",
                        io.opentelemetry.api.trace.TraceFlags.getSampled(), io.opentelemetry.api.trace.TraceState.getDefault()), Attributes.builder().put("private", "SYNTHETIC_LINK_SECRET").build())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("business.outcome", "matched");
            span.setAttribute("rag.result", "degraded");
            span.setAttribute("rpc.method", "SYNTHETIC_RPC_SECRET");
            span.setAttribute("http.route", "/SYNTHETIC_ROUTE_SECRET");
            span.setAttribute("operation", "SYNTHETIC_ENUM_SECRET");
            span.setAttribute("private", "SYNTHETIC_ATTRIBUTE_SECRET");
            span.setAttribute("db.query.text", "SELECT SYNTHETIC_SQL_SECRET");
            span.setStatus(StatusCode.ERROR, "SYNTHETIC_STATUS_SECRET");
            span.recordException(new IllegalStateException("SYNTHETIC_EXCEPTION_SECRET"));
            String url = args[0] + "/v1/graphs/builds/SYNTHETIC_BUILD_SECRET?knowledgeBase=SYNTHETIC_KB_SECRET"
                    + "&secret=SYNTHETIC_WECHAT_SECRET&js_code=SYNTHETIC_CODE_SECRET";
            HttpClient client = HttpClient.newHttpClient();
            var response = client.send(HttpRequest.newBuilder(URI.create(url))
                    .header("X-Secret", "SYNTHETIC_HEADER_SECRET").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !"synthetic-response".equals(response.body())) {
                throw new AssertionError("HTTP behavior changed");
            }
            System.out.println("PROBE_TRACE=" + span.getSpanContext().getTraceId());
        } finally {
            span.end();
        }
        GlobalOpenTelemetry.getTracer("SYNTHETIC_SCOPE_SECRET").spanBuilder("SYNTHETIC_SPAN_NAME_SECRET").startSpan().end();
        // 正常 JVM shutdown hook 刷新 agent 有界队列；业务无 SDK/flush 依赖。
    }
}
