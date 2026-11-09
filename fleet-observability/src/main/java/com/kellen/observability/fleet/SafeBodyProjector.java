package com.kellen.observability.fleet;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 只提取已声明类型的安全字段；无效、过限和未知自由正文不会以原始前缀回退。 */
public final class SafeBodyProjector {
    public enum Direction { REQUEST, RESPONSE }
    /** body 是已经投影的 JSON 字符串，omitted 是固定原因码，两者最多有一个非空。 */
    public record Projection(String body, String omitted) { }
    private final FleetObservabilityProperties properties;
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(8192)
                    .maxNumberLength(64).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> PROVIDERS = Set.of("google", "google_genai", "deepseek", "qwen", "openai", "anthropic", "ollama");

    public SafeBodyProjector(FleetObservabilityProperties properties) { this.properties = properties; }

    /** 投影普通 HTTP JSON；AI 自由正文只能走独立受信诊断策略。 */
    public Projection projectHttp(String method, String route, Direction direction, byte[] body,
            String contentType, boolean complete) {
        String media = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (media.startsWith("text/event-stream") || media.contains("stream+json") || media.contains("ndjson")) return omitted("stream");
        if (media.startsWith("multipart/")) return omitted("multipart");
        if (isAiRoute(route)) return omitted("policy");
        if (body.length == 0) return omitted(complete ? "empty" : "not_consumed");
        if (!complete) return omitted("incomplete");
        if (body.length > properties.getMaxBodyBytes()) return omitted("too_large");
        if (!(media.startsWith("application/json") || media.split(";", 2)[0].endsWith("+json"))) return omitted("binary");
        if (media.contains("charset=") && !(media.matches(".*charset=\\s*\"?(utf-8|us-ascii)\"?\\s*(;.*)?"))) return omitted("invalid_encoding");
        final String json;
        try { json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString(); }
        catch (CharacterCodingException failure) { return omitted("invalid_encoding"); }
        try {
            JsonNode input = mapper.readTree(json);
            if (input == null || !input.isObject()) return omitted("policy");
            Map<String, Object> output = new LinkedHashMap<>();
            if (direction == Direction.REQUEST) {
                if ("POST".equals(method) && "/api/rag/retrievals".equals(route)) {
                    integer(input, output, "topK", 1, 100); number(input, output, "similarityThreshold", 0, 1);
                    integer(input, output, "maxContextCharacters", 1, 65536); collectionName(input, output);
                } else {
                    page(input, output);
                }
            } else {
                envelope(input, output);
                if ("POST".equals(method) && "/api/rag/retrievals".equals(route)) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    bool(input.path("data"), data, "enabled"); bool(input.path("data"), data, "matched");
                    integer(input.path("data"), data, "sourceCount", 0, 1_000_000_000L);
                    if (!data.isEmpty()) output.put("data", data);
                } else if ("GET".equals(method) && Set.of("/api/ai/model-selection", "/api/agents/model").contains(route)) {
                    models(input, output);
                    Map<String, Object> data = new LinkedHashMap<>(); models(input.path("data"), data);
                    if (!data.isEmpty()) output.put("data", data);
                } else {
                    page(input, output); Map<String, Object> data = new LinkedHashMap<>(); page(input.path("data"), data);
                    if (!data.isEmpty()) output.put("data", data);
                }
            }
            return output.isEmpty() ? omitted("policy") : new Projection(mapper.writeValueAsString(output), null);
        } catch (RuntimeException failure) { return omitted("invalid_json"); }
    }

    /** 可选 AI 诊断只接受已定义的结构化投影；调用方不能用开关直接透传任意模型正文。 */
    public Optional<String> projectAiDiagnostic(String json) {
        if (!properties.isAiContentEnabled() || json == null || json.length() > 8192) return Optional.empty();
        try {
            JsonNode input = mapper.readTree(json);
            if (input == null || !input.isObject()) return Optional.empty();
            Map<String, Object> output = new LinkedHashMap<>();
            String role = input.path("role").asString("");
            if (Set.of("system", "user", "assistant", "tool").contains(role)) output.put("role", role);
            for (String key : List.of("messageCount", "documentCount", "characterCount")) integer(input, output, key, 0, 1_000_000_000L);
            models(input, output);
            if (output.isEmpty()) return Optional.empty();
            String projected = mapper.writeValueAsString(output);
            return projected.length() <= properties.getMaxAiContentCharacters() ? Optional.of(projected) : Optional.empty();
        } catch (RuntimeException failure) { return Optional.empty(); }
    }

    /** 仅从已验证投影提取受控业务错误码，永远不读取 errorMessage/Throwable。 */
    public Optional<String> businessErrorCode(Projection projection) {
        if (projection.body() == null) return Optional.empty();
        try {
            JsonNode code = mapper.readTree(projection.body()).path("code");
            String value = code.isString() ? code.asString() : code.isIntegralNumber() ? code.asString() : "";
            return value.matches("[A-Z][0-9]{4}|-1|[1-9][0-9]{0,5}") ? Optional.of(value) : Optional.empty();
        } catch (RuntimeException failure) { return Optional.empty(); }
    }

    static boolean isAiRoute(String route) {
        if (route == null) return false;
        return route.contains("/chat") || route.contains("patient") || route.contains("questionnaire");
    }
    private void envelope(JsonNode input, Map<String, Object> output) {
        bool(input, output, "success"); JsonNode code = input.path("code");
        if (code.isIntegralNumber() && code.canConvertToLong() && code.longValue() >= -1 && code.longValue() <= 999999) output.put("code", code.longValue());
        else if (code.isString() && code.asString().matches("0|[A-Z][0-9]{4}")) output.put("code", code.asString());
    }
    private void page(JsonNode input, Map<String, Object> output) {
        for (String key : List.of("page", "pageSize", "current", "size", "limit", "offset", "total", "pages")) integer(input, output, key, 0, 1_000_000_000L);
    }
    private void models(JsonNode input, Map<String, Object> output) {
        String provider = input.path("provider").asString("");
        if (PROVIDERS.contains(provider)) output.put("provider", provider);
        identifier(input, output, "model"); JsonNode supported = input.path("supportedProviders");
        if (supported.isArray() && supported.size() <= PROVIDERS.size()) {
            var values = new java.util.ArrayList<String>();
            for (JsonNode item : supported) { if (!item.isString() || !PROVIDERS.contains(item.asString())) return; values.add(item.asString()); }
            output.put("supportedProviders", values);
        }
    }
    private void bool(JsonNode input, Map<String, Object> output, String key) {
        if (input.path(key).isBoolean()) output.put(key, input.path(key).booleanValue());
    }
    private void integer(JsonNode input, Map<String, Object> output, String key, long min, long max) {
        JsonNode value = input.path(key);
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= min && value.longValue() <= max) output.put(key, value.longValue());
    }
    private void number(JsonNode input, Map<String, Object> output, String key, double min, double max) {
        JsonNode value = input.path(key);
        if (value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() >= min && value.doubleValue() <= max) output.put(key, value.doubleValue());
    }
    private void identifier(JsonNode input, Map<String, Object> output, String key) {
        JsonNode value = input.path(key);
        if (value.isString() && !value.asString().contains("://")
                && value.asString().matches("[A-Za-z][A-Za-z0-9._:/-]{0,95}")) output.put(key, value.asString());
    }
    private void collectionName(JsonNode input, Map<String, Object> output) {
        JsonNode value = input.path("collectionName");
        if (value.isString() && value.asString().matches("[A-Za-z0-9_-]{1,128}")) output.put("collectionName", value.asString());
    }
    private Projection omitted(String reason) { return new Projection(null, reason); }
}
