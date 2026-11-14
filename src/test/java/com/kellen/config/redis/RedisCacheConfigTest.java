package com.kellen.config.redis;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证缓存值使用 Spring Data Redis 4 的 Jackson 3 序列化器，并能按原类型读回。
 */
class RedisCacheConfigTest {

    @Test
    void usesJackson3GenericSerializer() {
        assertThat(RedisCacheConfig.valueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
    }

    @Test
    void roundTripsTypedValue() {
        RedisSerializer<Object> serializer = RedisCacheConfig.valueSerializer();
        CachedSample sample = new CachedSample();
        sample.setName("cache-sample");
        sample.setCount(42);
        sample.setUpdatedAt(LocalDateTime.of(2026, 11, 15, 9, 30, 5));

        Object restored = serializer.deserialize(serializer.serialize(sample));

        assertThat(restored).isInstanceOf(CachedSample.class);
        assertThat(restored).isEqualTo(sample);
    }

    @Data
    public static class CachedSample {
        private String name;
        private Integer count;
        private LocalDateTime updatedAt;
    }
}
