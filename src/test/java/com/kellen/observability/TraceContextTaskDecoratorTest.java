package com.kellen.observability;

import com.kellen.utils.context.TenantContextHolder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证异步任务拿到的是提交时的上下文：租户等 TransmittableThreadLocal 不沿用创建线程那次请求的值，OTel 链路照常传递。
 */
class TraceContextTaskDecoratorTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN = "00f067aa0ba902b7";

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        executor.shutdown();
    }

    @Test
    void taskSeesSubmittersTenantContext() throws Exception {
        TenantContextHolder.setTenantId("t1");
        executor.submit(() -> { }).get(5, TimeUnit.SECONDS); // 线程池的唯一线程在租户 t1 下创建。

        TenantContextHolder.setTenantId("t2");
        String seen = executor.submit(TenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS);

        assertThat(seen).isEqualTo("t2");
    }

    @Test
    void taskKeepsOtelContext() throws Exception {
        Span span = Span.wrap(SpanContext.create(TRACE, SPAN, TraceFlags.getSampled(), TraceState.getDefault()));
        String seen;
        try (Scope ignored = span.makeCurrent()) {
            seen = executor.submit(() -> Span.current().getSpanContext().getTraceId()).get(5, TimeUnit.SECONDS);
        }

        assertThat(seen).isEqualTo(TRACE);
    }
}
