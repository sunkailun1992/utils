package com.kellen.observability.fleet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;

/** 真正启动 Servlet 与 Reactive HTTP server，验证传输、flush、async 与脱敏事件。 */
class HttpTransportIntegrationTest {
    private static ConfigurableApplicationContext servlet;
    private static ConfigurableApplicationContext reactive;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @BeforeAll static void start() {
        servlet = start(ServletApp.class, WebApplicationType.SERVLET);
        reactive = start(ReactiveApp.class, WebApplicationType.REACTIVE);
        assertThat(reactive.getClass().getName()).contains("Reactive");
        assertThat(reactive.getBeansOfType(FleetWebFluxFilter.class)).hasSize(1);
    }
    @AfterAll static void stop() { if (reactive != null) reactive.close(); if (servlet != null) servlet.close(); }
    private static ConfigurableApplicationContext start(Class<?> application, WebApplicationType type) {
        return new SpringApplicationBuilder(application).web(type).properties(Map.of(
                "server.port", "0", "server.address", "127.0.0.1", "spring.main.banner-mode", "off",
                "logging.level.root", "OFF", "fleet.observability.enabled", "true",
                "spring.main.register-shutdown-hook", "false", "spring.main.web-application-type", type.name())).run();
    }
    @Test void servletJsonIsUnchangedAndLogsSafeTypedProjection() throws Exception { assertJson(servlet); }
    @Test void reactiveJsonIsUnchangedAndLogsSafeTypedProjection() throws Exception { assertJson(reactive); }
    private void assertJson(ConfigurableApplicationContext context) throws Exception {
        sink(context).clear();
        String input = "{\"topK\":3,\"query\":\"SYNTHETIC_SECRET\"}";
        var response = CLIENT.send(request(context, "/api/rag/retrievals").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(input)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("SYNTHETIC_SECRET");
        Map<String, Object> event = await(context, "/api/rag/retrievals");
        assertThat(event).containsEntry("http_status", 200).containsEntry("outcome", "success")
                .containsEntry("request_body_complete", true).containsEntry("response_body_complete", true)
                .containsEntry("request_bytes", (long) input.getBytes(StandardCharsets.UTF_8).length)
                .containsEntry("response_bytes", (long) response.body().getBytes(StandardCharsets.UTF_8).length);
        assertThat(event.get("request_body").toString()).contains("topK").doesNotContain("SECRET", "query");
        assertThat(event.get("response_body").toString()).contains("sourceCount").doesNotContain("SECRET", "context");
        assertThat(sink(context).events).hasSize(1);
    }
    @Test void servletWriterAndAsyncResponsesAreNotLost() throws Exception {
        sink(servlet).clear();
        var writer = CLIENT.send(request(servlet, "/fixture/writer").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(writer.body()).isEqualTo("{\"success\":true,\"msg\":\"中文\"}");
        assertThat(await(servlet, "/fixture/writer")).containsEntry("response_bytes", (long) writer.body().getBytes(StandardCharsets.UTF_8).length);
        sink(servlet).clear();
        var async = CLIENT.send(request(servlet, "/fixture/async").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(async.statusCode()).isEqualTo(200);
        assertThat(await(servlet, "/fixture/async")).containsEntry("outcome", "success");
        assertThat(sink(servlet).events).hasSize(1);
    }
    @Test void rejectedAndUnknownRequestsStillHaveOneSafeEvent() throws Exception {
        for (ConfigurableApplicationContext context : List.of(servlet, reactive)) {
            sink(context).clear();
            assertThat(CLIENT.send(request(context, "/fixture/denied").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            assertThat(await(context, "/fixture/denied")).containsEntry("outcome", "rejected");
            sink(context).clear();
            var missing = CLIENT.send(request(context, "/not-found/SYNTHETIC_SECRET?token=SYNTHETIC_SECRET").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(missing.statusCode()).isEqualTo(404);
            Map<String, Object> missingEvent = await(context, "UNMATCHED");
            assertThat(missingEvent.toString()).doesNotContain("SECRET", "token");
            assertThat(missingEvent).containsEntry("response_bytes", (long) missing.body().getBytes(StandardCharsets.UTF_8).length);
            assertThat(sink(context).events).hasSize(1);
        }
    }
    @Test void malformedRequestStillFinishesWithTheFinalErrorResponse() throws Exception {
        for (ConfigurableApplicationContext context : List.of(servlet, reactive)) {
            sink(context).clear();
            var result = CLIENT.send(request(context, "/api/rag/retrievals").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"topK\":SYNTHETIC_SECRET}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).isEqualTo(400);
            Map<String, Object> event = await(context, "/api/rag/retrievals");
            assertThat(event).containsEntry("http_status", 400).containsEntry("response_bytes", (long) result.body().getBytes(StandardCharsets.UTF_8).length);
            assertThat(event.toString()).doesNotContain("SECRET");
            assertThat(sink(context).events).hasSize(1);
        }
    }
    @Test void unhandledServerErrorHasFinalResponseAndNoRawException() throws Exception {
        for (ConfigurableApplicationContext context : List.of(servlet, reactive)) {
            sink(context).clear();
            var response = CLIENT.send(request(context, "/fixture/boom").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(500);
            Map<String, Object> event = await(context, "/fixture/boom");
            assertThat(event).containsEntry("http_status", 500).containsEntry("response_body_complete", true)
                    .containsEntry("response_bytes", (long) response.body().getBytes(StandardCharsets.UTF_8).length);
            assertThat(event.toString()).doesNotContain("SECRET"); assertThat(sink(context).events).hasSize(1);
        }
    }
    @Test void servletErrorDispatchDiscardsTheUncommittedPrefix() throws Exception {
        sink(servlet).clear();
        var response = CLIENT.send(request(servlet, "/fixture/boom-written").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(500); assertThat(response.body()).doesNotContain("SYNTHETIC_SECRET");
        assertThat(await(servlet, "/fixture/boom-written")).containsEntry("response_bytes", (long) response.body().getBytes(StandardCharsets.UTF_8).length)
                .containsEntry("response_body_complete", true);
    }
    @Test void servletSseFlushesBeforeCompletionAndNeverLogsBody() throws Exception {
        sink(servlet).clear();
        var response = CLIENT.send(request(servlet, "/fixture/sse").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            assertThat(new String(input.readNBytes(24), StandardCharsets.UTF_8)).contains("SYNTHETIC");
            assertThat(sink(servlet).events).isEmpty();
            input.readAllBytes();
        }
        assertThat(await(servlet, "/fixture/sse")).containsEntry("response_body_omitted", "stream").doesNotContainKey("response_body");
        assertThat(sink(servlet).events).hasSize(1);
    }
    @Test void reactiveSseCancellationIsObservedWithoutAggregation() throws Exception {
        sink(reactive).clear();
        var response = CLIENT.send(request(reactive, "/fixture/sse").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) { assertThat(input.readNBytes(24)).hasSize(24); }
        Map<String, Object> event = await(reactive, "/fixture/sse");
        assertThat(event).containsEntry("outcome", "cancelled").containsEntry("response_body_complete", false)
                .containsEntry("response_body_omitted", "stream").doesNotContainKey("response_body");
        assertThat(sink(reactive).events).hasSize(1);
    }
    @Test void servletSseClientDisconnectHasOneCancelledTerminal() throws Exception {
        sink(servlet).clear();
        var response = CLIENT.send(request(servlet, "/fixture/sse-cancel").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) { assertThat(input.readNBytes(24)).hasSize(24); }
        Map<String, Object> event = await(servlet, "/fixture/sse-cancel");
        assertThat(event).containsEntry("outcome", "cancelled").containsEntry("response_body_complete", false)
                .containsEntry("response_body_omitted", "stream").doesNotContainKey("response_body");
        assertThat(sink(servlet).events).hasSize(1);
    }
    @Test void largeBodiesAreForwardedButNeverProduceAPartialSecretPrefix() throws Exception {
        for (ConfigurableApplicationContext context : List.of(servlet, reactive)) {
            sink(context).clear();
            String input = "{\"topK\":3,\"query\":\"SYNTHETIC_SECRET" + "x".repeat(20000) + "\"}";
            var response = CLIENT.send(request(context, "/api/rag/retrievals").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(input)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).contains("SYNTHETIC_SECRET");
            var event = await(context, "/api/rag/retrievals");
            assertThat(event).containsEntry("request_body_omitted", "too_large").containsEntry("response_body_omitted", "too_large")
                    .doesNotContainKeys("request_body", "response_body");
            assertThat(event.get("request_bytes")).isEqualTo((long) input.getBytes(StandardCharsets.UTF_8).length);
            assertThat(event.get("response_bytes")).isEqualTo((long) response.body().getBytes(StandardCharsets.UTF_8).length);
        }
    }
    private static HttpRequest.Builder request(ConfigurableApplicationContext context, String path) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5));
    }
    private static CapturingSink sink(ConfigurableApplicationContext context) { return context.getBean(CapturingSink.class); }
    private static Map<String, Object> await(ConfigurableApplicationContext context, String route) throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            for (Map<String, Object> event : sink(context).events) if (route.equals(event.get("http_route"))) return event;
            Thread.sleep(20);
        }
        throw new AssertionError("missing terminal event for fixture route " + route + "; safe events=" + sink(context).events);
    }
    static final class CapturingSink implements FleetEventSink {
        final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
        @Override public void emit(Map<String, Object> fields) { events.add(fields); }
        void clear() { events.clear(); }
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration @Import(ServletController.class)
    static class ServletApp { @Bean CapturingSink fixtureSink() { return new CapturingSink(); } }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration @Import(ReactiveController.class)
    static class ReactiveApp { @Bean CapturingSink fixtureSink() { return new CapturingSink(); } }
    @RestController static class ServletController {
        @PostMapping("/api/rag/retrievals") Map<String, Object> json(@RequestBody Map<String, Object> input) { return result(input); }
        @GetMapping("/fixture/denied") ResponseEntity<?> denied() { return ResponseEntity.status(401).body(Map.of("code", "A0301", "message", "SYNTHETIC_SECRET")); }
        @GetMapping("/fixture/async") Callable<Map<String, Object>> async() { return () -> Map.of("success", true); }
        @GetMapping("/fixture/boom") Map<String, Object> boom() { throw new IllegalStateException("SYNTHETIC_SECRET"); }
        @GetMapping("/fixture/boom-written") void boomWritten(HttpServletResponse response) throws Exception {
            response.setContentType("application/json"); response.getOutputStream().write("SYNTHETIC_SECRET".getBytes(StandardCharsets.UTF_8));
            throw new IllegalStateException("SYNTHETIC_SECRET");
        }
        @GetMapping("/fixture/writer") void writer(HttpServletResponse response) throws Exception {
            response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.getWriter().write("{\"success\":true,\"msg\":\"中文\"}");
        }
        @GetMapping(value = "/fixture/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE) SseEmitter sse() {
            SseEmitter emitter = new SseEmitter(3000L);
            Thread writer = new Thread(() -> {
                try { emitter.send("SYNTHETIC_SECRET_FIRST_CHUNK"); Thread.sleep(150); emitter.send("SYNTHETIC_SECRET_LAST_CHUNK"); emitter.complete(); }
                catch (Exception ignored) { emitter.complete(); }
            }); writer.setDaemon(true); writer.start(); return emitter;
        }
        @GetMapping(value = "/fixture/sse-cancel", produces = MediaType.TEXT_EVENT_STREAM_VALUE) SseEmitter cancel() {
            SseEmitter emitter = new SseEmitter(3000L);
            Thread writer = new Thread(() -> {
                try { for (int n = 0; n < 20; n++) { emitter.send("SYNTHETIC_SECRET_CHUNK"); Thread.sleep(100); } emitter.complete(); }
                catch (Exception ignored) { emitter.complete(); }
            }); writer.setDaemon(true); writer.start(); return emitter;
        }
    }
    @RestController static class ReactiveController {
        @PostMapping("/api/rag/retrievals") Map<String, Object> json(@RequestBody Map<String, Object> input) { return result(input); }
        @GetMapping("/fixture/denied") ResponseEntity<?> denied() { return ResponseEntity.status(401).body(Map.of("code", "A0301", "message", "SYNTHETIC_SECRET")); }
        @GetMapping("/fixture/boom") Map<String, Object> boom() { throw new IllegalStateException("SYNTHETIC_SECRET"); }
        @GetMapping(value = "/fixture/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE) Flux<String> sse() {
            return Flux.just("SYNTHETIC_SECRET_FIRST_CHUNK").concatWith(Flux.interval(Duration.ofMillis(100)).map(n -> "SYNTHETIC_SECRET_NEXT_CHUNK"));
        }
    }
    private static Map<String, Object> result(Map<String, Object> input) {
        return Map.of("code", "0", "success", true, "data", Map.of("sourceCount", 3, "matched", true, "context", input.get("query")));
    }
}
