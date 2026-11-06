package com.kellen.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证真实 OTel 上下文快照与线程复用隔离，不信任业务 MDC 字段。 */
class TraceContextTest {
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String SPAN = "0123456789abcdef";

    @Test
    void capturesActiveSpanAndKeepsSnapshotAfterScopeEnds() {
        TraceContextSnapshot snapshot;
        try (Scope ignored = activeSpan().makeCurrent()) {
            snapshot = TraceContextSnapshot.capture();
        }
        assertThat(snapshot.traceId()).isEqualTo(TRACE);
        assertThat(snapshot.spanId()).isEqualTo(SPAN);
        assertThat(TraceContextSnapshot.capture().traceId()).isNull();
    }

    @Test
    void doesNotPromoteUntrustedBusinessMdcToTraceIdentity() {
        MDC.put("traceId", "conversation-or-caller-value");
        try {
            assertThat(TraceContextSnapshot.capture().traceId()).isNull();
            assertThat(TraceContextSnapshot.capture().spanId()).isNull();
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void decoratedTaskRestoresWorkerContextAfterSuccessAndFailure() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            var observedTrace = new java.util.concurrent.atomic.AtomicReference<String>();
            Runnable task;
            try (Scope ignored = activeSpan().makeCurrent()) {
                task = new TraceContextTaskDecorator().decorate(() -> {
                    observedTrace.set(TraceContextSnapshot.capture().traceId());
                    assertThat(TraceContextSnapshot.capture().traceId()).isEqualTo(TRACE);
                    throw new IllegalStateException("synthetic failure");
                });
            }
            executor.submit(() -> {
                try {
                    task.run();
                } catch (IllegalStateException expected) {
                    assertThat(TraceContextSnapshot.capture().traceId()).isNull();
                }
            }).get(5, TimeUnit.SECONDS);
            assertThat(observedTrace.get()).isEqualTo(TRACE);
            assertThat(executor.submit(() -> TraceContextSnapshot.capture().traceId())
                    .get(5, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private Span activeSpan() {
        return Span.wrap(SpanContext.create(TRACE, SPAN, TraceFlags.getSampled(), TraceState.getDefault()));
    }
}
