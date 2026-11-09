package com.kellen.observability.fleet;

import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.http.server.reactive.HttpHandlerDecoratorFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import java.util.concurrent.TimeoutException;

/**
 * Reactive 传输适配：原生 HttpHandler decorator 包含异常处理器的最终写出；WebFilter 只关联路由。
 * 不聚合响应，保留 writeAndFlushWith 分组、背压与取消。
 */
public final class FleetWebFluxFilter implements WebFilter, HttpHandlerDecoratorFactory, Ordered {
    private final FleetObservabilityProperties properties;
    private final SafeBodyProjector projector;
    private final FleetEventSink sink;
    public FleetWebFluxFilter(FleetObservabilityProperties properties, SafeBodyProjector projector, FleetEventSink sink) {
        this.properties = properties; this.projector = projector; this.sink = sink;
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }

    /** Spring WebHttpHandlerBuilder 自动发现该扩展，覆盖 WebExceptionHandler 在内的完整传输生命周期。 */
    @Override public HttpHandler apply(HttpHandler delegate) {
        return (request, response) -> Mono.deferContextual(context -> {
            ExchangeState state = new ExchangeState(request);
            ServerHttpRequestDecorator observedRequest = new ServerHttpRequestDecorator(request) {
                @Override public Flux<DataBuffer> getBody() {
                    return super.getBody().doOnNext(buffer -> {
                        state.route(); copy(buffer, state.log.request, state.log.captureMedia(state.log.requestType));
                    }).doOnComplete(state.log.request::completed);
                }
            };
            ObservedResponse observedResponse = new ObservedResponse(response, state);
            return delegate.handle(observedRequest, observedResponse).doOnError(failure -> failed(state.log, failure))
                    .doFinally(signal -> {
                        state.route(); HttpExchangeLog log = state.log; log.responseType = type(response.getHeaders());
                        if (signal == SignalType.CANCEL) cancelled(log);
                        if (signal != SignalType.ON_COMPLETE) log.responseBytesKnown = false;
                        Integer status = response.getStatusCode() == null ? null : response.getStatusCode().value();
                        if (status == null && signal == SignalType.ON_COMPLETE && response.isCommitted()) status = 200;
                        if (log.failure != null && status != null && status < 400 && !"cancelled".equals(log.outcome)) status = null;
                        log.finish(status, log.outcome, log.failure, signal == SignalType.ON_COMPLETE && log.responseBytesKnown);
                    });
        });
    }
    /** Filter 不另写事件，防止一次请求被传输层和 Controller 层重复记录。 */
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ExchangeState state = state(exchange.getResponse());
        if (state != null) state.exchange = exchange;
        return chain.filter(exchange).doOnError(failure -> {
            if (state != null) {
                state.log.failure = failure;
                if (failure instanceof TimeoutException) state.log.outcome = "timeout";
            }
        }).doFinally(signal -> { if (state != null) state.route(); });
    }
    private final class ExchangeState {
        final HttpExchangeLog log;
        volatile ServerWebExchange exchange;
        ExchangeState(ServerHttpRequest request) {
            log = new HttpExchangeLog(request.getMethod().name(), type(request.getHeaders()), projector, sink, properties.getMaxBodyBytes());
            if (request.getHeaders().getContentLength() == 0) log.request.completed();
        }
        void route() {
            ServerWebExchange current = exchange; if (current == null) return;
            if (current.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof org.springframework.web.method.HandlerMethod)
                log.matchedRoute(current.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
            // Path predicate 的值由 Gateway 在服务端匹配后设置，不依赖 Gateway 包或客户端 URI。
            log.matchedRoute(current.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayPredicateMatchedPathAttr"));
        }
    }
    private final class ObservedResponse extends ServerHttpResponseDecorator {
        final ExchangeState state;
        ObservedResponse(ServerHttpResponse response, ExchangeState state) { super(response); this.state = state; }
        private Flux<DataBuffer> observe(Publisher<? extends DataBuffer> body) {
            return Flux.<DataBuffer>from(body).doOnNext(buffer -> {
                state.route(); state.log.responseType = type(getHeaders());
                copy(buffer, state.log.response, state.log.captureMedia(state.log.responseType));
            }).doOnCancel(() -> cancelled(state.log)).doOnError(failure -> failed(state.log, failure));
        }
        @Override public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(observe(body)).doOnError(failure -> failed(state.log, failure)).doOnCancel(() -> cancelled(state.log));
        }
        @Override public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return super.writeAndFlushWith(Flux.from(body).map(this::observe).doOnCancel(() -> cancelled(state.log)))
                    .doOnError(failure -> failed(state.log, failure)).doOnCancel(() -> cancelled(state.log));
        }
    }
    private ExchangeState state(ServerHttpResponse response) {
        ServerHttpResponse current = response;
        while (current instanceof ServerHttpResponseDecorator decorator) {
            if (current instanceof ObservedResponse observed) return observed.state;
            current = decorator.getDelegate();
        }
        return null;
    }
    private static void cancelled(HttpExchangeLog log) { log.outcome = "cancelled"; log.responseBytesKnown = false; }
    private static void failed(HttpExchangeLog log, Throwable failure) {
        log.failure = failure; log.responseBytesKnown = false;
        if (failure instanceof TimeoutException) log.outcome = "timeout";
        else if (org.springframework.web.util.DisconnectedClientHelper.isClientDisconnectedException(failure)) log.outcome = "cancelled";
    }
    private static String type(org.springframework.http.HttpHeaders headers) {
        String encoding = headers.getFirst("Content-Encoding");
        return encoding == null || encoding.equalsIgnoreCase("identity") ? headers.getFirst("Content-Type") : "application/octet-stream";
    }
    private static void copy(DataBuffer buffer, BodyCapture capture, boolean retain) {
        try (var buffers = buffer.readableByteBuffers()) { while (buffers.hasNext()) capture.add(buffers.next(), retain); }
    }
}
