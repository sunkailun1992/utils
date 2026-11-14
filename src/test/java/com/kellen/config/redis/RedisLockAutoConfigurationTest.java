package com.kellen.config.redis;

import com.kellen.utils.redisson.LockUtil;
import com.kellen.utils.redisson.RedissonLocker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证锁工具只在有 RedissonClient（由 Redisson 官方 starter 提供）时装配，并设入 {@link LockUtil}。
 */
class RedisLockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisLockAutoConfiguration.class));

    @Test
    void registersLockerWhenRedissonClientPresent() {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock:probe")).thenReturn(lock);

        runner.withBean(RedissonClient.class, () -> client).run(context -> {
            assertThat(context).hasSingleBean(RedissonLocker.class);

            LockUtil.isLocked("lock:probe");

            verify(client).getLock("lock:probe");
        });
    }

    @Test
    void skipsWhenNoRedissonClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedissonLocker.class);
        });
    }
}
