package com.kellen.config.dubbo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Triple 集成测试的提供方：按方法记执行次数；{@link #onNextCall(Runnable)} 的回调在下一次方法执行时先跑
 * （此时请求已到提供方、响应还没写出）；key 为 {@code "boom"} 抛业务异常，为 {@code "slow"} 睡 1500 毫秒。
 */
final class RetryProbeProvider implements RetryProbeService {

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    private final AtomicReference<Runnable> nextCall = new AtomicReference<>();

    /**
     * @param hook 下一次方法执行时先跑的回调
     */
    void onNextCall(Runnable hook) {
        nextCall.set(hook);
    }

    /**
     * @param method 方法名
     * @return 该方法执行过的次数
     */
    int calls(String method) {
        AtomicInteger count = calls.get(method);
        return count == null ? 0 : count.get();
    }

    @Override
    public String read(String key) {
        return handle("read", key);
    }

    @Override
    public String write(String key) {
        return handle("write", key);
    }

    @Override
    public CompletableFuture<String> readAsync(String key) {
        return CompletableFuture.completedFuture(handle("readAsync", key));
    }

    private String handle(String method, String key) {
        calls.computeIfAbsent(method, name -> new AtomicInteger()).incrementAndGet();
        Runnable hook = nextCall.getAndSet(null);
        if (hook != null) {
            hook.run();
        }
        if ("boom".equals(key)) {
            throw new IllegalStateException("boom");
        }
        if ("slow".equals(key)) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return method + ":" + key;
    }
}
