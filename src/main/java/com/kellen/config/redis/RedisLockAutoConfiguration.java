package com.kellen.config.redis;

import com.kellen.utils.redisson.LockUtil;
import com.kellen.utils.redisson.RedissonLocker;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁工具的自动配置。
 *
 * <p>Redis 连接、{@code StringRedisTemplate} 与 {@link RedissonClient} 由 Spring Boot 与 Redisson 官方
 * {@code redisson-spring-boot-starter} 按 {@code spring.data.redis.*} 自动配置；这里只在已有
 * {@link RedissonClient} 时装配 {@link RedissonLocker} 并设入 {@link LockUtil}，测试里排除 Redisson
 * 自动配置时自动跳过。</p>
 *
 * @author 孙凯伦
 */
@AutoConfiguration(afterName = {
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        "org.redisson.spring.starter.RedissonAutoConfigurationV4"})
@ConditionalOnBean(RedissonClient.class)
public class RedisLockAutoConfiguration {

    /**
     * 基于 Redisson 的锁实现，并作为 {@link LockUtil} 静态门面的底层实现。
     *
     * @param redissonClient Redisson 客户端
     * @return 锁实现
     */
    @Bean
    public RedissonLocker redissonLocker(RedissonClient redissonClient) {
        RedissonLocker locker = new RedissonLocker(redissonClient);
        LockUtil.setLocker(locker);
        return locker;
    }
}
