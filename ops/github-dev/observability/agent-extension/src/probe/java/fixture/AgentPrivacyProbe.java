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
        var remote = io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
                "ffffffffffffffffffffffffffffffff", "1111111111111111",
                io.opentelemetry.api.trace.TraceFlags.getDefault(), io.opentelemetry.api.trace.TraceState.getDefault());
        Span span = tracer.spanBuilder("rag.retrieve")
                .setParent(io.opentelemetry.context.Context.root().with(Span.wrap(remote)))
                .addLink(io.opentelemetry.api.trace.SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef",
                        io.opentelemetry.api.trace.TraceFlags.getSampled(), io.opentelemetry.api.trace.TraceState.getDefault()), Attributes.builder().put("private", "SYNTHETIC_LINK_SECRET").build())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("business.outcome", "matched");
            span.setAttribute("rag.result", "degraded");
            span.setAttribute("rpc.method", "SYNTHETIC_RPC_SECRET");
            span.setAttribute("http.route", "/SYNTHETIC_ROUTE_SECRET");
            span.setAttribute("operation", "SYNTHETIC_ENUM_SECRET");
            span.setAttribute("traffic.route.mode", "SYNTHETIC_ROUTE_MODE_SECRET");
            span.setAttribute("traffic.route.decision", 123L);
            span.setAttribute("traffic.route.hop", "SYNTHETIC_ROUTE_HOP_SECRET");
            span.setAttribute("traffic.route.grant", "SYNTHETIC_ROUTE_GRANT_SECRET");
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
            Span model = tracer.spanBuilder("ai.model.call").startSpan();
            model.setAttribute("gen_ai.operation.name", "chat");
            model.setAttribute("gen_ai.provider.name", "gcp.gemini");
            model.setAttribute("gen_ai.request.model", "fixture-model-v1");
            model.setAttribute("gen_ai.usage.input_tokens", 17L);
            model.setAttribute("gen_ai.usage.output_tokens", 11L);
            model.setAttribute("gen_ai.usage.reasoning.output_tokens", 3L);
            model.setAttribute("fleet.ai.usage.available", true);
            model.setAttribute("rpc.system.name", "apache_dubbo");
            model.setAttribute("rpc.service", "com.kellen.rpc.user.UserRpcService");
            model.setAttribute("rpc.method", "findUser");
            model.setAttribute("traffic.route.mode", "canary");
            model.setAttribute("traffic.route.decision", "selected");
            model.setAttribute("traffic.route.hop", "dubbo_consumer");
            model.setAttribute("fleet.ai.reasoning.present", false);
            model.setAttribute("gen_ai.input.messages", "SYNTHETIC_PROMPT_SECRET");
            model.setAttribute("gen_ai.output.messages", "SYNTHETIC_COMPLETION_SECRET");
            model.setAttribute("fleet.ai.private", "SYNTHETIC_AI_SECRET");
            model.end();
            System.out.println("PROBE_TRACE=" + span.getSpanContext().getTraceId());
        } finally {
            span.end();
        }
        GlobalOpenTelemetry.getTracer("SYNTHETIC_SCOPE_SECRET").spanBuilder("SYNTHETIC_SPAN_NAME_SECRET").startSpan().end();
        // 正常 JVM shutdown hook 刷新 agent 有界队列；业务无 SDK/flush 依赖。
    }
}
