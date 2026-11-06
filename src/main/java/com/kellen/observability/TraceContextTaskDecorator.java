package com.kellen.observability;

import io.opentelemetry.context.Context;
import org.springframework.core.task.TaskDecorator;

/**
 * 为公共异步线程池捕获 OTel 上下文，并在任务完成或失败后恢复工作线程的旧作用域。
 * 不复制业务用户、租户或任意 MDC 字段，已有身份上下文机制保持独立。
 */
public final class TraceContextTaskDecorator implements TaskDecorator {
    /**
     * 捕获提交线程的上下文；try-with-resources 覆盖异常及 CallerRunsPolicy 路径。
     *
     * @param runnable 原任务
     * @return 不泄漏上下文的包装任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        return Context.current().wrap(runnable);
    }
}
