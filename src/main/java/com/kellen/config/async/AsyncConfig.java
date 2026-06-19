package com.kellen.config.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * <p>提供 {@code @Async} 使用的公共线程池：核心 10、最大 100、队列 10，满载时由调用线程兜底执行，
 * 关闭时等待在途任务完成。</p>
 *
 * @author 孙凯伦
 */
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    /**
     * 构建 {@code @Async} 公共线程池。
     *
     * @return 线程池执行器
     */
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
        //设置核心线程数
        threadPool.setCorePoolSize(10);
        //设置最大线程数
        threadPool.setMaxPoolSize(100);
        //线程池所使用的缓冲队列
        threadPool.setQueueCapacity(10);
        // 拒绝策略， 由当前执行线程执行
        threadPool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //等待任务在关机时完成--表明等待所有线程执行完
        threadPool.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间 （默认为0，此时立即停止），并没等待xx秒后强制停止
        threadPool.setAwaitTerminationSeconds(60);
        //  线程名称前缀
        threadPool.setThreadNamePrefix("gbw");
        // 初始化线程
        threadPool.initialize();
        return threadPool;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return null;
    }
}
