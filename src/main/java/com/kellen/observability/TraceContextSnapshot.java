package com.kellen.observability;

import io.opentelemetry.api.trace.Span;

/**
 * 真实链路标识的不可变快照，供日志在异步落库前捕获上下文。
 *
 * <p>不从业务 traceId、请求参数或 MDC 回退，避免把会话 ID 或客户端输入提升为链路身份。
 * 本类型只使用 OTel API；未挂 agent 时返回空标识，不创建另一个 SDK。</p>
 *
 * @param traceId 有效 OTel trace ID，无活动 span 时为 null
 * @param spanId 有效 OTel span ID，无活动 span 时为 null
 */
public record TraceContextSnapshot(String traceId, String spanId) {
    /**
     * 在调用线程捕获标识，返回对象可以安全传给异步日志消费者。
     *
     * @return 当前有效标识或空快照
     */
    public static TraceContextSnapshot capture() {
        var context = Span.current().getSpanContext();
        return context.isValid()
                ? new TraceContextSnapshot(context.getTraceId(), context.getSpanId())
                : new TraceContextSnapshot(null, null);
    }
}
