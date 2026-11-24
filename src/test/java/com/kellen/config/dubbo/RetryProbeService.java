package com.kellen.config.dubbo;

import java.util.concurrent.CompletableFuture;

/**
 * Triple 集成测试用的服务接口：{@code read} 在引用上声明幂等，{@code write} 不声明。
 */
public interface RetryProbeService {

    String read(String key);

    String write(String key);

    CompletableFuture<String> readAsync(String key);
}
