package com.kellen.observability.privacy;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class AgentEgressTest {
    @Test
    void realAgentExportsOnlySafeMetadataBeforeCollectorWhilePreservingHttpPropagation() throws Exception {
        List<byte[]> bodies = new CopyOnWriteArrayList<>();
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/v1/traces", exchange -> {
            bodies.add(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        receiver.createContext("/v1/graphs", exchange -> {
            request.set(exchange.getRequestURI().toString());
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] response = "synthetic-response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        receiver.start();
        var output = Files.createTempFile("fleet-agent-egress-", ".log");
        try {
            String endpoint = "http://127.0.0.1:" + receiver.getAddress().getPort();
            var builder = new ProcessBuilder(System.getProperty("java.bin"),
                    "-javaagent:" + System.getProperty("agent.jar"),
                    "-cp", System.getProperty("probe.classpath"), "fixture.AgentPrivacyProbe", endpoint);
            builder.environment().keySet().removeIf(key -> key.startsWith("OTEL_")
                    || List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(key));
            builder.environment().putAll(Map.ofEntries(
                    Map.entry("OTEL_JAVAAGENT_EXTENSIONS", System.getProperty("privacy.jar") + "," + System.getProperty("verifier.jar")),
                    Map.entry("OTEL_SERVICE_NAME", "fixture-java"),
                    Map.entry("OTEL_RESOURCE_ATTRIBUTES", "service.version=1.2.3,deployment.environment.name=github-dev,private=SYNTHETIC_RESOURCE_SECRET"),
                    Map.entry("OTEL_EXPORTER_OTLP_ENDPOINT", endpoint),
                    Map.entry("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf"),
                    Map.entry("OTEL_METRICS_EXPORTER", "none"), Map.entry("OTEL_LOGS_EXPORTER", "none"),
                    Map.entry("OTEL_TRACES_SAMPLER", "always_on"),
                    // 反例：调用方试图重新开启 events，扩展必须覆盖。
                    Map.entry("OTEL_SPAN_EVENT_COUNT_LIMIT", "128"),
                    Map.entry("OTEL_BSP_SCHEDULE_DELAY", "100")));
            Process child = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!child.waitFor(30, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                fail("child JVM timed out");
            }
            String log = Files.readString(output);
            assertEquals(0, child.exitValue(), log);
            assertTrue(log.contains("opentelemetry-javaagent - version: 2.33.1"), log);
            var prequeue = log.lines().filter(line -> line.startsWith("PRIVACY_PREQUEUE_EVENTS=")).toList();
            assertTrue(prequeue.size() >= 3, "missing pre-queue SPI verification: " + log);
            assertTrue(prequeue.stream().allMatch(line -> line.equals("PRIVACY_PREQUEUE_EVENTS=0")),
                    "raw exception event entered SDK batch queue");
            assertTrue(request.get().contains("secret=SYNTHETIC_WECHAT_SECRET"));
            assertNotNull(traceparent.get(), "automatic JDK HTTP propagation missing");
            assertFalse(bodies.isEmpty(), "actual OTLP request missing: " + log);
            var evidence = java.nio.file.Path.of(System.getProperty("evidence.dir"));
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("child.log"), log);
            List<Span> spans = new ArrayList<>();
            int requestIndex = 0;
            for (byte[] body : bodies) {
                String raw = new String(body, StandardCharsets.ISO_8859_1);
                assertFalse(raw.contains("SYNTHETIC_"), "raw pre-Collector OTLP leaked synthetic secret");
                assertFalse(raw.contains("url.full"));
                assertFalse(raw.contains("exception."));
                assertFalse(raw.contains("/v1/graphs"));
                Files.write(evidence.resolve("otlp-" + requestIndex++ + ".protobuf"), body);
                var payload = ExportTraceServiceRequest.parseFrom(body);
                payload.getResourceSpansList().forEach(resource -> {
                    assertEquals(Map.of("service.name", "fixture-java", "service.version", "1.2.3",
                            "deployment.environment.name", "github-dev"), strings(resource.getResource().getAttributesList()));
                    resource.getScopeSpansList().forEach(scope -> spans.addAll(scope.getSpansList()));
                });
            }
            Span business = spans.stream().filter(span -> span.getName().equals("rag.retrieve")).findFirst().orElseThrow();
            assertEquals(1, business.getLinksCount(), "synthetic valid link must survive without attributes");
            assertEquals("matched", strings(business.getAttributesList()).get("business.outcome"));
            assertEquals("degraded", strings(business.getAttributesList()).get("rag.result"));
            for (Span span : spans) {
                assertEquals(0, span.getEventsCount());
                assertEquals("", span.getStatus().getMessage());
                span.getLinksList().forEach(link -> assertEquals(0, link.getAttributesCount()));
            }
            Span http = spans.stream().filter(span -> span.getKind() == Span.SpanKind.SPAN_KIND_CLIENT).findFirst().orElseThrow();
            assertEquals(business.getTraceId(), http.getTraceId());
            assertEquals(business.getSpanId(), http.getParentSpanId());
            String trace = java.util.HexFormat.of().formatHex(http.getTraceId().toByteArray());
            String spanId = java.util.HexFormat.of().formatHex(http.getSpanId().toByteArray());
            assertTrue(traceparent.get().startsWith("00-" + trace + "-" + spanId + "-"));
            assertEquals(1, Integer.parseInt(traceparent.get().substring(53), 16) & 1);
            System.out.println("PASS actual agent 2.33.1 + API-only 1.59.0: pre-Collector OTLP="
                    + bodies.size() + " requests, spans=" + spans.size() + ", no secrets, HTTP propagation retained");
        } finally {
            receiver.stop(0);
            Files.deleteIfExists(output);
        }
    }

    private static Map<String, String> strings(List<KeyValue> values) {
        return values.stream().collect(Collectors.toMap(KeyValue::getKey, v -> v.getValue().getStringValue()));
    }
}
