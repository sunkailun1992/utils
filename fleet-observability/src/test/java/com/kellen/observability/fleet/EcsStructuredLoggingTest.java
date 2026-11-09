package com.kellen.observability.fleet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.FilteredClassLoader;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用真正 Boot 4 ECS encoder；ListAppender 的 KV 不能证明最终文件格式保留这些字段。 */
class EcsStructuredLoggingTest {
    private static final String PRIVATE = "SYNTHETIC_PRIVATE_MUST_NOT_SURVIVE";
    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    @Test void realFleetSinkKeepsHttpProjectionWithCurrentInclude() throws Exception {
        JsonNode output = capture("com.kellen.observability.fleet.events", () -> {
            MDC.put("trace_id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            MDC.put("span_id", "bbbbbbbbbbbbbbbb");
            new Slf4jFleetEventSink().emit(Map.of(
                "event", "http.server.exchange", "summary", "GET /api/fixture 200 7ms success",
                "http_method", "GET", "http_route", "/api/fixture", "http_status", 200, "duration_ms", 7L,
                "request_body", "{\"topK\":3}", "response_body_complete", true,
                "trace_id", "11111111111111111111111111111111", "span_id", "2222222222222222"));
        });
        assertThat(output.path("event").asString()).isEqualTo("http.server.exchange");
        assertThat(output.path("request_body").asString()).isEqualTo("{\"topK\":3}");
        assertThat(output.path("http_status").intValue()).isEqualTo(200);
        assertThat(output.path("response_body_complete").booleanValue()).isTrue();
        assertThat(output.path("trace_id").asString()).isEqualTo("11111111111111111111111111111111");
    }

    @Test void realAiKeyValuesKeepTypedUsageAndDropUnknownMdcAndRawContent() throws Exception {
        JsonNode output = capture("com.kellen.agent.service.AiModelCallObserver", () -> {
            MDC.put("private", PRIVATE);
            MDC.put("summary", PRIVATE);
            MDC.put("trace_id", "11111111111111111111111111111111");
            MDC.put("span_id", "2222222222222222");
            LoggerFactory.getLogger("com.kellen.agent.service.AiModelCallObserver").atInfo()
                    .addKeyValue("event", "ai.model.call").addKeyValue("summary", "AI patient_analysis finished: success")
                    .addKeyValue("ai_operation", "patient_analysis").addKeyValue("ai_provider", "qwen")
                    .addKeyValue("ai_model", "synthetic-model").addKeyValue("ai_input_tokens", 7L)
                    .addKeyValue("ai_output_tokens", 13L).addKeyValue("ai_usage_available", true)
                    .addKeyValue("ai_input", PRIVATE).addKeyValue("private", PRIVATE)
                    .setCause(new IllegalStateException(PRIVATE)).log(PRIVATE);
        });
        assertThat(output.path("event").asString()).isEqualTo("ai.model.call");
        assertThat(output.path("ai_input_tokens").longValue()).isEqualTo(7L);
        assertThat(output.path("ai_output_tokens").longValue()).isEqualTo(13L);
        assertThat(output.path("ai_usage_available").booleanValue()).isTrue();
        assertThat(output.path("trace_id").asString()).isEqualTo("11111111111111111111111111111111");
        assertThat(output.path("span_id").asString()).isEqualTo("2222222222222222");
        assertThat(output.toString()).doesNotContain(PRIVATE, "ai_input\"", "stack_trace", "\"message\"");
    }

    @Test void frameworkFailureHasSafeLoggerAndExceptionTypeWithoutExceptionText() throws Exception {
        JsonNode output = capture("org.springframework.boot.SpringApplication", () -> LoggerFactory.getLogger("org.springframework.boot.SpringApplication")
                .atError().setCause(new IllegalStateException(PRIVATE, new IllegalArgumentException(PRIVATE))).log(PRIVATE));
        assertThat(output.path("error_type").asString()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(output.path("summary").asString()).contains("org.springframework.boot.SpringApplication", "java.lang.IllegalArgumentException");
        assertThat(output.toString()).doesNotContain(PRIVATE, "stack_trace", "\"message\"");
        assertThat(output.has("error")).isFalse();
    }

    @Test void pinnedBootDefaultContextRejectsDuplicateIdentityEvenWhenValuesAgree() {
        assertThatThrownBy(() -> capture("com.kellen.observability.fleet.events", () -> {
            MDC.put("trace_id", "11111111111111111111111111111111");
            new Slf4jFleetEventSink().emit(Map.of("event", "http.server.exchange", "trace_id", "11111111111111111111111111111111"));
        }, false, true)).isInstanceOf(AssertionError.class).hasMessageContaining("duplicate:trace_id");
    }

    @Test void malformedKnownValuesAndUnknownLoggerCannotSmuggleContent() throws Exception {
        Object opaque = new Object() { @Override public String toString() { throw new AssertionError("must never stringify opaque values"); } };
        JsonNode output = capture("com.kellen.agent.service.AiModelCallObserver", () -> LoggerFactory.getLogger("com.kellen.agent.service.AiModelCallObserver")
                .atInfo().addKeyValue("event", "ai.model.call").addKeyValue("ai_input_tokens", true)
                .addKeyValue("ai_output_tokens", -1L).addKeyValue("ai_reasoning_tokens", opaque)
                .addKeyValue("ai_usage_available", "true").addKeyValue("duration_ms", Double.NaN)
                .addKeyValue("http_status", "200").addKeyValue("ai_input", PRIVATE).log(PRIVATE));
        for (String key : List.of("ai_input_tokens", "ai_output_tokens", "ai_reasoning_tokens", "ai_usage_available", "duration_ms", "http_status", "ai_input"))
            assertThat(output.has(key)).as(key).isFalse();
        JsonNode unknown = capture("org.springframework.boot.SpringApplication", () -> LoggerFactory.getLogger("org.springframework.boot.SpringApplication")
                .atWarn().addKeyValue("event", "http.server.exchange").addKeyValue("request_body", PRIVATE)
                .addKeyValue("summary", PRIVATE).addKeyValue("trace_id", "11111111111111111111111111111111").log(PRIVATE));
        assertThat(unknown.has("event")).isFalse(); assertThat(unknown.has("request_body")).isFalse();
        assertThat(unknown.has("trace_id")).isFalse(); assertThat(unknown.toString()).doesNotContain(PRIVATE);
    }

    @Test void observingFormatterRequiresDisabledDefaultContext() {
        assertThatThrownBy(() -> capture("com.kellen.observability.fleet.events", () -> {}, true, true))
                .hasMessage("FLEET_LOGGING_CONTEXT_CONTRACT_REQUIRED");
    }

    @Test void spiCanLoadWithoutLogbackOnTheRuntimeClasspath() throws Exception {
        var location = FleetStructuredLoggingCustomizer.class.getProtectionDomain().getCodeSource().getLocation();
        try (var parent = new FilteredClassLoader("ch.qos.logback");
             var loader = new java.net.URLClassLoader(new java.net.URL[]{location}, parent) {
                 @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                     if (!name.startsWith(FleetStructuredLoggingCustomizer.class.getName())) return super.loadClass(name, resolve);
                     synchronized (getClassLoadingLock(name)) {
                         Class<?> type = findLoadedClass(name); if (type == null) type = findClass(name);
                         if (resolve) resolveClass(type); return type;
                     }
                 }
             }) {
            Object spi = loader.loadClass(FleetStructuredLoggingCustomizer.class.getName()).getConstructor(Environment.class)
                    .newInstance(new MockEnvironment().withProperty("fleet.observability.enabled", "true"));
            @SuppressWarnings("unchecked")
            var customizer = (org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer<Object>) spi;
            var writer = org.springframework.boot.json.JsonWriter.<Object>of(members -> {
                members.add("fixture", ignored -> true);
                customizer.customize(members);
            });
            assertThat(writer.writeToString(new Object())).isEqualTo("{\"fixture\":true}");
        }
    }

    private JsonNode capture(String name, Runnable invocation) throws Exception {
        return capture(name, invocation, true, false);
    }

    private JsonNode capture(String name, Runnable invocation, boolean enabled, boolean includeContext) throws Exception {
        String template = Files.readString(Path.of("../ops/github-dev/nacos/DEFAULT_GROUP/logging.yml"));
        String include = template.lines().map(String::strip).filter(line -> line.startsWith("include: \"")).findFirst().orElseThrow()
                .substring("include:".length()).strip().replace("\"", "");
        MockEnvironment environment = new MockEnvironment().withProperty("logging.structured.json.include", include)
                .withProperty("fleet.observability.enabled", Boolean.toString(enabled))
                .withProperty("logging.structured.json.context.include", Boolean.toString(includeContext))
                .withProperty("logging.structured.ecs.service.name", "fixture-java")
                .withProperty("logging.structured.ecs.service.version", "1.2.3")
                .withProperty("logging.structured.ecs.service.environment", "github-dev");
        LoggerContext encoderContext = new LoggerContext();
        encoderContext.putObject(Environment.class.getName(), environment);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(encoderContext); encoder.setFormat("ecs"); encoder.start();
        List<JsonNode> records = new ArrayList<>();
        List<String> encodingFailures = new ArrayList<>();
        List<String> parsingFailures = new ArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                byte[] encoded;
                try { encoded = encoder.encode(event); }
                catch (RuntimeException failure) {
                    String message = failure.getMessage() == null ? "" : failure.getMessage();
                    encodingFailures.add(failure.getClass().getSimpleName()
                            + (message.contains("Duplicate") ? ":duplicate" : "")
                            + (message.contains("trace_id") ? ":trace_id" : "")
                            + (message.contains("span_id") ? ":span_id" : "")
                            + (message.contains("summary") ? ":summary" : ""));
                    return;
                }
                try { records.add(STRICT_JSON.readTree(encoded)); }
                catch (RuntimeException failure) { parsingFailures.add(failure.getClass().getSimpleName()); }
            }
        };
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory()); appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(name);
        Level oldLevel = logger.getLevel(); boolean oldAdditive = logger.isAdditive();
        Map<String, String> oldMdc = MDC.getCopyOfContextMap();
        logger.setLevel(Level.INFO); logger.setAdditive(false); logger.addAppender(appender);
        try {
            invocation.run(); assertThat(encodingFailures).as("real ECS encoding failures").isEmpty();
            assertThat(parsingFailures).as("JSON decoding failures").isEmpty();
            assertThat(records).hasSize(1); return records.get(0);
        }
        finally {
            logger.detachAppender(appender); logger.setLevel(oldLevel); logger.setAdditive(oldAdditive);
            if (oldMdc == null) MDC.clear(); else MDC.setContextMap(oldMdc);
            appender.stop(); encoder.stop(); encoderContext.stop();
        }
    }
}
