package com.kellen.observability.fleet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实际 Servlet filter/stream 回归，覆盖未消费、部分读写、async 终态与失败隔离。 */
class ServletLifecycleTest {
    @Test void noBodyIsReadBeforeApplicationAndPartialInputIsNotMarkedComplete() throws Exception {
        var request = request(); var response = new MockHttpServletResponse(); var events = new ArrayList<Map<String, Object>>();
        filter(events::add).doFilter(request, response, (req, res) -> {
            req.getInputStream().read(); ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204);
        });
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("request_bytes", 1L).containsEntry("request_body_complete", false)
                .containsEntry("request_body_omitted", "incomplete");
    }
    @Test void unusedInputRemainsUnconsumed() throws Exception {
        var events = new ArrayList<Map<String, Object>>();
        filter(events::add).doFilter(request(), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(events.get(0)).containsEntry("request_bytes", 0L).containsEntry("request_body_omitted", "not_consumed");
    }
    @Test void getWithoutLengthDoesNotPretendItsUnreadBodyIsComplete() throws Exception {
        var events = new ArrayList<Map<String, Object>>();
        var request = new MockHttpServletRequest("GET", "/api/ai/model-selection");
        filter(events::add).doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        assertThat(events.get(0)).containsEntry("request_body_complete", false).containsEntry("request_body_omitted", "not_consumed");
    }
    @Test void asynchronousTimeoutAndCompletionEmitOnce() throws Exception {
        var request = request(); request.setAsyncSupported(true);
        var response = new MockHttpServletResponse(); var events = new ArrayList<Map<String, Object>>();
        filter(events::add).doFilter(request, response, (req, res) -> req.startAsync());
        assertThat(events).isEmpty();
        var context = (org.springframework.mock.web.MockAsyncContext) request.getAsyncContext();
        var event = new AsyncEvent(context, request, response);
        for (var listener : List.copyOf(context.getListeners())) listener.onTimeout(event);
        for (var listener : List.copyOf(context.getListeners())) listener.onComplete(event);
        for (var listener : List.copyOf(context.getListeners())) listener.onComplete(event);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("outcome", "timeout").containsEntry("response_body_complete", false)
                .doesNotContainKey("response_body");
    }
    @Test void realSpanIsCapturedBeforeAsyncBoundaryAndArbitraryMdcCannotImpersonateIt() throws Exception {
        var events = new ArrayList<Map<String, Object>>();
        MDC.put("traceId", "SYNTHETIC_SECRET");
        var span = Span.wrap(SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault()));
        try (var scope = span.makeCurrent()) {
            filter(events::add).doFilter(request(), new MockHttpServletResponse(), (req, res) -> { });
        } finally { MDC.remove("traceId"); }
        assertThat(events.get(0)).containsEntry("trace_id", "0123456789abcdef0123456789abcdef")
                .containsEntry("span_id", "0123456789abcdef");
        events.clear();
        filter(events::add).doFilter(request(), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(events.get(0)).doesNotContainKeys("trace_id", "span_id");
    }
    @Test void writeFailureDoesNotClaimCompleteOrPersistExceptionMessage() {
        var events = new ArrayList<Map<String, Object>>();
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    @Override public void write(int value) throws IOException { throw new IOException("SYNTHETIC_SECRET"); }
                    @Override public boolean isReady() { return true; }
                    @Override public void setWriteListener(WriteListener listener) { }
                };
            }
        };
        assertThatThrownBy(() -> filter(events::add).doFilter(request(), response, (req, res) -> res.getOutputStream().write(1))).isInstanceOf(IOException.class);
        assertThat(events).hasSize(1); assertThat(events.get(0)).containsEntry("outcome", "failure").containsEntry("response_body_complete", false);
        assertThat(events.get(0).toString()).doesNotContain("SECRET");
    }
    @Test void loggerFailureCannotChangeSuccessfulResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter(event -> { throw new IllegalStateException("SYNTHETIC_SECRET"); }).doFilter(request(), response, (req, res) -> res.getOutputStream().write(7));
        assertThat(response.getContentAsByteArray()).containsExactly((byte) 7);
    }
    @Test void committedExceptionCannotWaitForAnErrorDispatchThatWillNotHappen() {
        var events = new ArrayList<Map<String, Object>>(); var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter(events::add).doFilter(request(), response, (req, res) -> {
            res.getOutputStream().write(1); res.flushBuffer(); throw new jakarta.servlet.ServletException("SYNTHETIC_SECRET");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(events).hasSize(1); assertThat(events.get(0)).containsEntry("outcome", "failure")
                .containsEntry("http_status", 200).containsEntry("response_body_complete", false);
    }
    @Test void errorDispatchSendingErrorAgainCompletesOnceAndDiscardsBothPrefixes() throws Exception {
        var events = new ArrayList<Map<String, Object>>(); var response = new MockHttpServletResponse();
        var request = request(); var filter = filter(events::add);
        filter.doFilter(request, response, (req, res) -> {
            res.getWriter().write("SYNTHETIC_SECRET");
            ((jakarta.servlet.http.HttpServletResponse) res).sendError(403);
        });
        assertThat(events).isEmpty();
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR); response.setCommitted(false);
        filter.doFilter(request, response, (req, res) -> {
            res.getWriter().write("SYNTHETIC_ERROR_PREFIX");
            ((jakarta.servlet.http.HttpServletResponse) res).sendError(403);
        });
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("http_status", 403).containsEntry("outcome", "rejected")
                .containsEntry("response_bytes", 0L).doesNotContainKey("response_body");
        assertThat(events.toString()).doesNotContain("SYNTHETIC_SECRET", "SYNTHETIC_ERROR_PREFIX");
    }
    @Test void committedSendErrorFailureCannotArmAnImpossibleErrorDispatch() {
        var events = new ArrayList<Map<String, Object>>(); var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter(events::add).doFilter(request(), response, (req, res) -> {
            res.getOutputStream().write(1); res.flushBuffer();
            ((jakarta.servlet.http.HttpServletResponse) res).sendError(403);
        })).isInstanceOf(IllegalStateException.class);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("http_status", 200).containsEntry("outcome", "failure")
                .containsEntry("response_body_complete", false);
    }
    @Test void asyncRedispatchUsesCurrentRequestViewWithoutDoubleCountingTheTee() throws Exception {
        var events = new ArrayList<Map<String, Object>>(); var initial = request(); initial.setAsyncSupported(true);
        var response = new MockHttpServletResponse(); var filter = filter(events::add);
        filter.doFilter(initial, response, (req, res) -> req.startAsync());
        var context = initial.getAsyncContext();
        var dispatch = new jakarta.servlet.http.HttpServletRequestWrapper((jakarta.servlet.http.HttpServletRequest) context.getRequest()) {
            @Override public jakarta.servlet.DispatcherType getDispatcherType() { return jakarta.servlet.DispatcherType.ASYNC; }
            @Override public String getRequestURI() { return "/fixture/final"; }
            @Override public boolean isAsyncStarted() { return false; }
        };
        filter.doFilter(dispatch, response, (req, res) -> {
            assertThat(((jakarta.servlet.http.HttpServletRequest) req).getRequestURI()).isEqualTo("/fixture/final");
            assertThat(req.getDispatcherType()).isEqualTo(jakarta.servlet.DispatcherType.ASYNC);
            req.getInputStream().readAllBytes();
        });
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("request_bytes", (long) initial.getContentAsByteArray().length);
    }
    @Test void responseResetDoesNotCountDiscardedOutput() throws Exception {
        var events = new ArrayList<Map<String, Object>>(); var response = new MockHttpServletResponse();
        filter(events::add).doFilter(request(), response, (req, res) -> {
            var http = (jakarta.servlet.http.HttpServletResponse) res; http.setContentType("application/json");
            http.getOutputStream().write("SYNTHETIC_SECRET".getBytes(StandardCharsets.UTF_8));
            http.resetBuffer(); http.getOutputStream().write("{\"success\":true}".getBytes(StandardCharsets.UTF_8));
        });
        assertThat(events.get(0)).containsEntry("response_bytes", 16L).containsEntry("response_body", "{\"success\":true}");
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }
    @Test void writerResetAndSplitSurrogatePreserveContainerOutputAndByteCount() throws Exception {
        var events = new ArrayList<Map<String, Object>>(); var response = new MockHttpServletResponse();
        filter(events::add).doFilter(request(), response, (req, res) -> {
            var http = (jakarta.servlet.http.HttpServletResponse) res; http.setContentType("application/json"); http.setCharacterEncoding("UTF-8");
            var writer = http.getWriter(); writer.write("SYNTHETIC_SECRET"); http.resetBuffer();
            writer.write("{\"success\":true,\"msg\":\""); writer.write('\uD83D'); writer.write('\uDE03'); writer.write("\"}");
        });
        String expected = "{\"success\":true,\"msg\":\"😃\"}";
        assertThat(response.getContentAsString()).isEqualTo(expected);
        assertThat(events.get(0)).containsEntry("response_bytes", (long) expected.getBytes(StandardCharsets.UTF_8).length)
                .containsEntry("response_body", "{\"success\":true}");
    }
    @Test void threadReuseAndConcurrentRequestsCannotShareTraceOrBodyState() throws Exception {
        var events = new java.util.concurrent.CopyOnWriteArrayList<Map<String, Object>>();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        var filter = filter(events::add);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int n = 1; n <= 18; n++) {
                final int index = n;
                futures.add(executor.submit(() -> {
                    String trace = String.format("%032x", index); String id = String.format("%016x", index);
                    var span = Span.wrap(SpanContext.create(trace, id, TraceFlags.getSampled(), TraceState.getDefault()));
                    try (var scope = span.makeCurrent()) {
                        var request = request(); request.setContent(("{\"topK\":" + index + "}").getBytes(StandardCharsets.UTF_8));
                        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> req.getInputStream().readAllBytes());
                    } catch (Exception failure) { throw new AssertionError(failure); }
                }));
            }
            for (var future : futures) future.get();
            assertThat(events).hasSize(18);
            for (Map<String, Object> event : events) {
                int index = Integer.parseInt(event.get("trace_id").toString(), 16);
                assertThat(event.get("request_body")).isEqualTo("{\"topK\":" + index + "}");
            }
        } finally { executor.shutdownNow(); }
    }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/rag/retrievals");
        request.setContentType("application/json"); request.setContent("{\"topK\":3,\"query\":\"SYNTHETIC_SECRET\"}".getBytes(StandardCharsets.UTF_8));
        try { request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(this, getClass().getDeclaredMethod("unusedInputRemainsUnconsumed"))); }
        catch (NoSuchMethodException failure) { throw new AssertionError(failure); }
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/rag/retrievals");
        return request;
    }
    private FleetServletFilter filter(FleetEventSink sink) {
        var properties = new FleetObservabilityProperties(); return new FleetServletFilter(properties, new SafeBodyProjector(properties), sink);
    }
}
