package com.kellen.security.oauth2;

import com.github.benmanes.caffeine.cache.Ticker;
import com.kellen.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务上下文缓存：本地 Caffeine → Redis（按用户版本号分键）→ 加载器。Redis 用内存表模拟，时间用可拨动的时钟。
 */
@ExtendWith(OutputCaptureExtension.class)
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
                .enabled(true).tenantIds(List.of(tenantId)).idpSubject("kc-" + userId).build());
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
        assertThat(user.getIdpSubject()).isEqualTo("kc-u1");
    }

    /**
     * 2.0.7 及更早写进 Redis 的副本没有 idpSubject：照常读出，该字段为空。
     */
    @Test
    void redisPayloadWrittenBeforeIdpSubjectStillReads() {
        redisData.put("fleet:security:ctx:u1:0:_", "{\"status\":\"OK\",\"user\":{\"userId\":\"u1\",\"username\":\"name-u1\","
                + "\"tenantId\":\"t1\",\"authorities\":[\"a:b\"],\"enabled\":true,\"tenantIds\":[\"t1\"]}}");
        SecurityContextResult fromRedis = cache().get("u1", null, loader(okUser("u1", "t1")));
        assertThat(loads).hasValue(0);
        assertThat(fromRedis.getUser().getUserId()).isEqualTo("u1");
        assertThat(fromRedis.getUser().getIdpSubject()).isNull();
    }

    /**
     * Redis 副本与 Keycloak 访问令牌同寿（5 分钟）：版本自增失败时，旧副本最多再用这么久。
     */
    @Test
    void redisCopyLivesAsLongAsAnAccessToken() {
        new SecurityUserCache(redis, Duration.ofSeconds(30)).get("u1", null, loader(okUser("u1", "t1")));
        verify(values).set(eq("fleet:security:ctx:u1:0:_"), anyString(), eq(Duration.ofMinutes(5)));
    }

    /**
     * 版本自增失败时，本实例与其他实例都会继续读到同版本号下的 Redis 旧副本，最多 Redis 时长加本地时长后才读到改动。
     */
    @Test
    void failedVersionBumpReportsTheRealStalenessBound(CapturedOutput output) {
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        new SecurityUserCache(redis, Duration.ofSeconds(30), Duration.ofMinutes(5), 10_000, ticker).bumpVersion("u1");
        assertThat(output.getAll()).contains("安全上下文版本自增失败").contains("330 秒");
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
