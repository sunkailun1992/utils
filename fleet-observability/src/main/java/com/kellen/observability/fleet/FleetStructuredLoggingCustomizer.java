package com.kellen.observability.fleet;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 固定安全成员替代 Boot 的自由 context pairs，防止 MDC/KVP 重名使整条 ECS 编码失败。 */
public final class FleetStructuredLoggingCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {
    private final boolean enabled;
    private final boolean defaultContextDisabled;

    public FleetStructuredLoggingCustomizer(Environment environment) {
        enabled = environment.getProperty("fleet.observability.enabled", Boolean.class, false);
        defaultContextDisabled = !environment.getProperty("logging.structured.json.context.include", Boolean.class, true);
    }

    @Override public void customize(JsonWriter.Members<Object> members) {
        if (!enabled || !ClassUtils.isPresent("ch.qos.logback.classic.spi.ILoggingEvent", getClass().getClassLoader())) return;
        if (!defaultContextDisabled) throw new IllegalStateException("FLEET_LOGGING_CONTEXT_CONTRACT_REQUIRED");
        LogbackMembers.register(members);
    }

    /** 分离类加载：没有 Logback 的消费者仍能加载公开 SPI，不增加日志实现运行依赖。 */
    private static final class LogbackMembers {
        private static final String HTTP_LOGGER = "com.kellen.observability.fleet.events";
        private static final String AI_LOGGER = "com.kellen.agent.service.AiModelCallObserver";
        private static final Map<String, Integer> STRINGS = Map.ofEntries(
                Map.entry("traffic_route_hop", 32), Map.entry("traffic_route_mode", 16),
                Map.entry("traffic_route_decision", 16), Map.entry("event", 64), Map.entry("summary", 512), Map.entry("http_method", 16), Map.entry("http_route", 200),
                Map.entry("request_body", 8192), Map.entry("response_body", 8192),
                Map.entry("request_body_omitted", 32), Map.entry("response_body_omitted", 32),
                Map.entry("error_type", 120), Map.entry("error_code", 80), Map.entry("outcome", 32), Map.entry("stage", 80),
                Map.entry("trace_id", 32), Map.entry("span_id", 16), Map.entry("ai_operation", 80),
                Map.entry("ai_provider", 80), Map.entry("ai_model", 128), Map.entry("ai_response_model", 128),
                Map.entry("ai_prompt_key", 128), Map.entry("ai_prompt_version", 128), Map.entry("ai_prompt_source", 32),
                Map.entry("ai_reasoning_kind", 32), Map.entry("ai_finish_reason", 32),
                Map.entry("ai_input_summary", 512), Map.entry("ai_output_summary", 512));
        private static final Set<String> COUNTS = Set.of("request_bytes", "response_bytes", "ai_input_tokens", "ai_output_tokens",
                "ai_reasoning_tokens", "ai_input_characters", "ai_output_characters", "ai_message_count", "ai_document_count");
        private static final Set<String> FLAGS = Set.of("request_body_complete", "response_body_complete", "ai_usage_available", "ai_reasoning_present");
        private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT", "OTHER");

        static void register(JsonWriter.Members<Object> members) {
            Set<String> names = new LinkedHashSet<>(STRINGS.keySet()); names.addAll(COUNTS); names.addAll(FLAGS);
            names.addAll(List.of("duration_ms", "http_status"));
            for (String name : names) members.add(name, event -> value(event, name)).whenNotNull();
        }

        private static Object value(Object source, String key) {
            if (!(source instanceof ch.qos.logback.classic.spi.ILoggingEvent event)) return null;
            try {
                boolean trusted = HTTP_LOGGER.equals(event.getLoggerName()) || AI_LOGGER.equals(event.getLoggerName());
                Pair pair = trusted ? pair(event, key) : Pair.ABSENT;
                if (key.equals("trace_id") || key.equals("span_id")) {
                    Object identity = pair.present ? pair.value : event.getMDCPropertyMap().get(key);
                    return identity(identity, key.equals("trace_id") ? 32 : 16);
                }
                if (key.equals("summary")) {
                    String summary = string(pair.value, 512);
                    return summary != null ? summary : diagnosticSummary(event);
                }
                if (key.equals("error_type") && !pair.present) return exceptionType(event);
                if (!trusted || !pair.present || key.startsWith("ai_") && !AI_LOGGER.equals(event.getLoggerName())) return null;
                if (COUNTS.contains(key)) {
                    Long number = integer(pair.value);
                    return number != null && number >= 0 && (!key.startsWith("ai_") || number <= 1_000_000_000L) ? number : null;
                }
                if (FLAGS.contains(key)) return pair.value instanceof Boolean ? pair.value : null;
                if (key.equals("http_status")) {
                    Long number = integer(pair.value); return number != null && number >= 100 && number <= 599 ? number : null;
                }
                if (key.equals("duration_ms")) {
                    if (pair.value instanceof Double number) return Double.isFinite(number) && number >= 0 && number <= Long.MAX_VALUE ? number : null;
                    if (pair.value instanceof Float number) return Float.isFinite(number) && number >= 0 ? number : null;
                    Long number = integer(pair.value); return number != null && number >= 0 ? number : null;
                }
                String text = string(pair.value, STRINGS.get(key));
                if (text == null) return null;
                if (key.equals("event")) {
                    return Set.of("http.server.exchange", "ai.model.call", TrafficRouteObservation.EVENT).contains(text)
                            ? text : null;
                }
                if (key.startsWith("traffic_route_")) {
                    return HTTP_LOGGER.equals(event.getLoggerName())
                            && TrafficRouteObservation.EVENT.equals(pair(event, "event").value)
                            && TrafficRouteObservation.allowed(key, text) ? text : null;
                }
                if (key.equals("http_method")) return METHODS.contains(text) ? text : null;
                if (key.equals("http_route")) return text.equals("UNMATCHED") || text.startsWith("/") && text.matches("[/A-Za-z0-9_{}:*?.-]+") && !text.contains("://") ? text : null;
                if (key.equals("request_body") || key.equals("response_body")) {
                    Pair kind = pair(event, "event");
                    return HTTP_LOGGER.equals(event.getLoggerName()) && "http.server.exchange".equals(kind.value) ? text : null;
                }
                if (key.equals("ai_model") || key.equals("ai_response_model") || key.equals("ai_prompt_key") || key.equals("ai_prompt_version"))
                    return text.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}") && !text.contains("://") ? text : null;
                if (key.equals("error_type")) return safeClass(text, 120) != null || Set.of("server", "validation", "timeout", "cancelled", "io").contains(text) ? text : null;
                return text;
            } catch (RuntimeException ignored) { return null; }
        }

        private static Pair pair(ch.qos.logback.classic.spi.ILoggingEvent event, String key) {
            List<org.slf4j.event.KeyValuePair> pairs = event.getKeyValuePairs();
            if (pairs == null || pairs.size() > 128) return Pair.ABSENT;
            Pair selected = Pair.ABSENT;
            for (org.slf4j.event.KeyValuePair pair : pairs) {
                if (key.equals(pair.key)) {
                    if (selected.present) return new Pair(true, null);
                    selected = new Pair(true, pair.value);
                }
            }
            return selected;
        }

        private static String identity(Object value, int width) {
            if (!(value instanceof String text) || text.length() != width || !text.matches("[0-9a-fA-F]+") || text.matches("0+")) return null;
            return text.toLowerCase(java.util.Locale.ROOT);
        }

        private static Long integer(Object value) {
            return value instanceof Long number ? number : value instanceof Integer number ? number.longValue()
                    : value instanceof Short number ? number.longValue() : value instanceof Byte number ? number.longValue() : null;
        }

        private static String string(Object value, Integer limit) {
            return value instanceof String text && limit != null && text.length() <= limit ? text : null;
        }

        private static String safeClass(String value, int limit) {
            return value != null && value.length() <= limit && value.matches("[A-Za-z_$][A-Za-z0-9_.$]*") ? value : null;
        }

        private static String exceptionType(ch.qos.logback.classic.spi.ILoggingEvent event) {
            ch.qos.logback.classic.spi.IThrowableProxy cause = event.getThrowableProxy(); String last = null;
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                String type = safeClass(cause.getClassName(), 120); if (type != null) last = type;
            }
            return last;
        }

        private static String diagnosticSummary(ch.qos.logback.classic.spi.ILoggingEvent event) {
            String logger = safeClass(event.getLoggerName(), 200); String error = exceptionType(event);
            return event.getLevel().toString() + " " + (logger == null ? "unknown" : logger) + (error == null ? "" : " exception=" + error);
        }

        private record Pair(boolean present, Object value) { private static final Pair ABSENT = new Pair(false, null); }
    }
}
