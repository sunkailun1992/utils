package com.netflix.hystrix.strategy.concurrency;


import com.alibaba.ttl.TtlCallable;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.PlatformSpecific;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @ClassName HystrixConcurrencyStrategy
 * @Description 重写Hystrix, 传值问题
 * @Author 孙凯伦
 *
 * @Email 376253703@qq.com
 * @Time 2021/8/6 9:16 上午
 */
@SuppressWarnings(value = "all")
public abstract class HystrixConcurrencyStrategy {
    private static final Logger logger = LoggerFactory.getLogger(HystrixConcurrencyStrategy.class);

    public HystrixConcurrencyStrategy() {
    }

    public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixProperty<Integer> corePoolSize, HystrixProperty<Integer> maximumPoolSize, HystrixProperty<Integer> keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        ThreadFactory threadFactory = getThreadFactory(threadPoolKey);
        int dynamicCoreSize = (Integer)corePoolSize.get();
        int dynamicMaximumSize = (Integer)maximumPoolSize.get();
        if (dynamicCoreSize > dynamicMaximumSize) {
            logger.error("Hystrix ThreadPool configuration at startup for : " + threadPoolKey.name() + " is trying to set coreSize = " + dynamicCoreSize + " and maximumSize = " + dynamicMaximumSize + ".  Maximum size will be set to " + dynamicCoreSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, (long)(Integer)keepAliveTime.get(), unit, workQueue, threadFactory);
        } else {
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, (long)(Integer)keepAliveTime.get(), unit, workQueue, threadFactory);
        }
    }

    public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
        ThreadFactory threadFactory = getThreadFactory(threadPoolKey);
        boolean allowMaximumSizeToDivergeFromCoreSize = (Boolean)threadPoolProperties.getAllowMaximumSizeToDivergeFromCoreSize().get();
        int dynamicCoreSize = (Integer)threadPoolProperties.coreSize().get();
        int keepAliveTime = (Integer)threadPoolProperties.keepAliveTimeMinutes().get();
        int maxQueueSize = (Integer)threadPoolProperties.maxQueueSize().get();
        BlockingQueue<Runnable> workQueue = this.getBlockingQueue(maxQueueSize);
        if (allowMaximumSizeToDivergeFromCoreSize) {
            int dynamicMaximumSize = (Integer)threadPoolProperties.maximumSize().get();
            if (dynamicCoreSize > dynamicMaximumSize) {
                logger.error("Hystrix ThreadPool configuration at startup for : " + threadPoolKey.name() + " is trying to set coreSize = " + dynamicCoreSize + " and maximumSize = " + dynamicMaximumSize + ".  Maximum size will be set to " + dynamicCoreSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
                return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, (long)keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
            } else {
                return new ThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, (long)keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
            }
        } else {
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, (long)keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
        }
    }

    private static ThreadFactory getThreadFactory(final HystrixThreadPoolKey threadPoolKey) {
        return !PlatformSpecific.isAppEngineStandardEnvironment() ? new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "hystrix-" + threadPoolKey.name() + "-" + this.threadNumber.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        } : PlatformSpecific.getAppEngineThreadFactory();
    }

    public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
        return (BlockingQueue)(maxQueueSize <= 0 ? new SynchronousQueue() : new LinkedBlockingQueue(maxQueueSize));
    }

    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        return TtlCallable.get(callable);
    }

    public <T> HystrixRequestVariable<T> getRequestVariable(HystrixRequestVariableLifecycle<T> rv) {
        return new HystrixLifecycleForwardingRequestVariable(rv);
    }
}
