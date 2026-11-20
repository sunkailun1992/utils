package com.kellen.security.oauth2;

import com.github.benmanes.caffeine.cache.Ticker;
import com.kellen.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务上下文缓存：本地 Caffeine → Redis（按用户版本号分键）→ 加载器。Redis 用内存表模拟，时间用可拨动的时钟。
 */
class SecurityUserCacheTest {

    private final Map<String, String> redisData = new ConcurrentHashMap<>();

    private final FakeTicker ticker = new FakeTicker();

    private StringRedisTemplate redis;

    private ValueOperations<String, String> values;

    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redisData.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> redisData.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisData.getOrDefault(key, "0")) + 1;
            redisData.put(key, String.valueOf(next));
            return next;
        });
    }

    private SecurityUserCache cache() {
        return new SecurityUserCache(redis, Duration.ofSeconds(30), Duration.ofMinutes(30), 10_000, ticker);
    }

    private Supplier<SecurityContextResult> loader(SecurityContextResult result) {
        return () -> {
            loads.incrementAndGet();
            return result;
        };
    }

    private static SecurityContextResult okUser(String userId, String tenantId) {
        return SecurityContextResult.ok(SecurityUser.builder().userId(userId).username("name-" + userId).tenantId(tenantId)
                .deptId("d1").dataScope("DEPT").dataScopeDeptIds(List.of("d1", "d2")).authorities(List.of("a:b", "ROLE_admin"))
                .enabled(true).tenantIds(List.of(tenantId)).build());
    }

    @Test
    void loadsOncePerUserWithinTtl() {
        SecurityUserCache cache = cache();
        SecurityContextResult result = okUser("u1", "t1");
        cache.get("u1", null, loader(result));
        ticker.advance(Duration.ofSeconds(29));
        cache.get("u1", null, loader(result));
        assertThat(loads).hasValue(1);
        assertThat(redisData).containsKey("fleet:security:ctx:u1:0:_");
    }

    @Test
    void redisPayloadSurvivesLocalExpiryAndRoundTripsUser() {
        SecurityUserCache cache = cache();
        cache.get("u1", "t1", loader(okUser("u1", "t1")));
        ticker.advance(Duration.ofSeconds(31));
        SecurityContextResult fromRedis = cache.get("u1", "t1", loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(1);
        SecurityUser user = fromRedis.getUser();
        assertThat(fromRedis.getStatus()).isEqualTo(SecurityContextResult.Status.OK);
        assertThat(user.getUserId()).isEqualTo("u1");
        assertThat(user.getUsername()).isEqualTo("name-u1");
        assertThat(user.getTenantId()).isEqualTo("t1");
        assertThat(user.getDeptId()).isEqualTo("d1");
        assertThat(user.getDataScope()).isEqualTo("DEPT");
        assertThat(user.getDataScopeDeptIds()).containsExactly("d1", "d2");
        assertThat(user.getAuthorities()).containsExactly("a:b", "ROLE_admin");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getTenantIds()).containsExactly("t1");
    }

    @Test
    void bumpVersionSwitchesRedisKeyAndReloads() {
        SecurityUserCache cache = cache();
        cache.get("u1", null, loader(okUser("u1", "t1")));
        cache.bumpVersion("u1");
        assertThat(redisData).containsEntry("fleet:security:ctx:v:u1", "1");
        cache.get("u1", null, loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(2);
        assertThat(redisData).containsKeys("fleet:security:ctx:u1:0:_", "fleet:security:ctx:u1:1:_");
    }

    @Test
    void anotherInstanceSeesBumpAfterItsLocalTtl() {
        SecurityUserCache first = cache();
        SecurityUserCache second = cache();
        first.get("u1", null, loader(okUser("u1", "t1")));
        second.get("u1", null, loader(okUser("u1", "t1")));
        first.bumpVersion("u1");
        ticker.advance(Duration.ofSeconds(31));
        second.get("u1", null, loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(2);
    }

    @Test
    void tenantIsPartOfTheKey() {
        SecurityUserCache cache = cache();
        cache.get("u1", "t1", loader(okUser("u1", "t1")));
        cache.get("u1", "t2", loader(okUser("u1", "t2")));
        assertThat(loads).hasValue(2);
        assertThat(redisData).containsKeys("fleet:security:ctx:u1:0:t1", "fleet:security:ctx:u1:0:t2");
    }

    @Test
    void onlyOkResultsAreCached() {
        SecurityUserCache cache = cache();
        SecurityContextResult disabled = SecurityContextResult.of(SecurityContextResult.Status.DISABLED);
        cache.get("u2", null, loader(disabled));
        cache.get("u2", null, loader(disabled));
        assertThat(loads).hasValue(2);
        assertThat(redisData).isEmpty();
    }

    @Test
    void redisUnavailableFallsBackToLoader() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        SecurityUserCache cache = cache();
        SecurityContextResult result = cache.get("u1", null, loader(okUser("u1", "t1")));
        assertThat(result.getUser().getUserId()).isEqualTo("u1");
        assertThat(loads).hasValue(1);
        cache.bumpVersion("u1");
        cache.get("u1", null, loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(2);
    }

    @Test
    void worksWithoutRedis() {
        SecurityUserCache cache = new SecurityUserCache(null, Duration.ofSeconds(30), Duration.ofMinutes(30), 10_000, ticker);
        cache.get("u1", null, loader(okUser("u1", "t1")));
        cache.get("u1", null, loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(1);
        cache.bumpVersion("u1");
        cache.get("u1", null, loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(2);
    }

    /**
     * 可拨动的时钟。
     */
    static final class FakeTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }
}
