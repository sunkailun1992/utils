package com.kellen.observability.fleet;

import io.opentelemetry.api.trace.Span;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一个传输请求的安全状态；异步 redispatch/完成回调共享同一个一次性终态。 */
final class HttpExchangeLog {
    final BodyCapture request;
    final BodyCapture response;
    final String method;
    private final SafeBodyProjector projector;
    private final FleetEventSink sink;
    private final long start = System.nanoTime();
    private final AtomicBoolean emitted = new AtomicBoolean();
    private final Map<String, String> trace = new LinkedHashMap<>();
    volatile String route = "UNMATCHED";
    volatile String requestType;
    volatile String responseType;
    volatile Throwable failure;
    volatile String outcome;
    volatile boolean responseBytesKnown = true;

    HttpExchangeLog(String method, String requestType, SafeBodyProjector projector,
            FleetEventSink sink, int limit) {
        this.method = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT").contains(method) ? method : "OTHER";
        this.requestType = requestType; this.projector = projector; this.sink = sink;
        request = new BodyCapture(limit); response = new BodyCapture(limit);
        var context = Span.current().getSpanContext();
        if (context.isValid()) { trace.put("trace_id", context.getTraceId()); trace.put("span_id", context.getSpanId()); }
    }
    /** route 必须来自框架匹配结果，不回退原始 URL。 */
    void matchedRoute(Object pattern) {
        if (pattern == null) return;
        String value = pattern.toString();
        if (value.length() <= 180 && value.matches("[/A-Za-z0-9_{}:*?.-]+")) route = value;
    }
    boolean captureMedia(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        return !SafeBodyProjector.isAiRoute(route) && (value.startsWith("application/json") || value.split(";", 2)[0].endsWith("+json"));
    }
    void finish(Integer status, String outcomeOverride, Throwable failure, boolean responseComplete) {
        if (!emitted.compareAndSet(false, true)) return;
        try {
            if (responseComplete && !Set.of("cancelled", "timeout").contains(outcomeOverride == null ? "" : outcomeOverride)) response.completed();
            Map<String, Object> fields = new LinkedHashMap<>(); fields.put("event", "http.server.exchange");
            fields.put("http_method", method); fields.put("http_route", route);
            if (status != null && status >= 100 && status <= 599) fields.put("http_status", status);
            long elapsed = Math.max(0, (System.nanoTime() - start) / 1_000_000); fields.put("duration_ms", elapsed);
            String outcome = outcomeOverride;
            if (outcome == null) outcome = failure != null || status != null && status >= 400 ? "failure" : "success";
            if (status != null && (status == 401 || status == 403)) outcome = "rejected";
            fields.put("outcome", outcome);
            fields.put("request_bytes", request.bytes());
            if (responseBytesKnown) fields.put("response_bytes", response.bytes());
            fields.put("request_body_complete", request.complete()); fields.put("response_body_complete", response.complete());
            projection(fields, "request", projector.projectHttp(method, route, SafeBodyProjector.Direction.REQUEST, request.snapshot(), requestType, request.complete()));
            var responseProjection = projector.projectHttp(method, route, SafeBodyProjector.Direction.RESPONSE, response.snapshot(), responseType, response.complete());
            projection(fields, "response", responseProjection);
            projector.businessErrorCode(responseProjection).ifPresent(code -> fields.put("error_code", code));
            if (failure != null) fields.put("error_type", errorType(failure));
            fields.putAll(trace);
            fields.put("summary", method + " " + route + " " + (status == null ? "unknown" : status) + " " + elapsed + "ms " + outcome);
            sink.emit(Map.copyOf(fields));
        } catch (RuntimeException ignored) { /* 观测失败不能改变业务响应，也不能回退打印原始输入。 */ }
        finally { request.discard(); response.discard(); }
    }
    private void projection(Map<String, Object> fields, String prefix, SafeBodyProjector.Projection projection) {
        if (projection.body() != null) fields.put(prefix + "_body", projection.body());
        if (projection.omitted() != null) fields.put(prefix + "_body_omitted", projection.omitted());
    }
    private String errorType(Throwable failure) {
        if (failure instanceof TimeoutException) return "timeout";
        if (failure instanceof CancellationException) return "cancelled";
        if (failure instanceof IOException) return "io";
        if (failure instanceof IllegalArgumentException) return "validation";
        return "server";
    }
}
