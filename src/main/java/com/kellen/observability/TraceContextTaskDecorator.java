package com.kellen.observability;

import com.alibaba.ttl.TtlRunnable;
import io.opentelemetry.context.Context;
import org.springframework.core.task.TaskDecorator;

/**
 * 为公共异步线程池传递提交线程的上下文：OTel 链路，以及租户、用户、数据源等基于
 * TransmittableThreadLocal 的上下文；任务结束后恢复工作线程原有的值，不复制 MDC。
 *
 * <p>只靠 TransmittableThreadLocal 本身，线程池复用线程时会沿用创建该线程那次请求的值，
 * 所以必须在提交时用 {@link TtlRunnable} 捕获、在执行时回放（与芋道 ruoyi-vue-pro 的做法一致）。</p>
 */
public final class TraceContextTaskDecorator implements TaskDecorator {
    /**
     * 捕获提交线程的上下文；异常及 CallerRunsPolicy 路径同样在执行后恢复。
     *
     * @param runnable 原任务
     * @return 带提交时上下文、不泄漏到后续任务的包装任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        return TtlRunnable.get(Context.current().wrap(runnable));
    }
}
