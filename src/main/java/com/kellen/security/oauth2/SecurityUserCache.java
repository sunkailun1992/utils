package com.kellen.security.oauth2;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.kellen.utils.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 业务安全上下文的两级缓存：本地 Caffeine → Redis → 加载器（Dubbo 调 user 服务）。
 *
 * <p>本地缓存按「用户 + 活动租户」存，过期前不访问 Redis。Redis 的键带用户版本号
 * {@code fleet:security:ctx:<userId>:<版本>:<租户或_>}，版本键 {@code fleet:security:ctx:v:<userId>}
 * 由 user 服务在改动用户时调用 {@link #bumpVersion(String)} 自增：本实例立刻失效，其他实例在本地缓存过期后读到新版本。
 * 版本键不设过期，避免过期后版本归零又命中同号旧数据。</p>
 *
 * <p>只缓存加载成功的结果；用户不存在、停用、租户被拒每次都重新确认。Redis 不可用时直接走加载器，不影响请求。</p>
 *
 * @author 孙凯伦
 */
public class SecurityUserCache {

    /**
     * 业务上下文键前缀。
     */
    static final String KEY_PREFIX = "fleet:security:ctx:";

    /**
     * 版本键前缀。
     */
    static final String VERSION_KEY_PREFIX = KEY_PREFIX + "v:";

    /**
     * 本地缓存上限。
     */
    static final long DEFAULT_MAXIMUM_SIZE = 10_000;

    /**
     * Redis 副本的存活时长。
     */
    static final Duration DEFAULT_REDIS_TTL = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(SecurityUserCache.class);

    private final StringRedisTemplate redis;

    private final Duration redisTtl;

    private final Cache<String, SecurityContextResult> local;

    /**
     * 构造缓存：Redis 副本 30 分钟、本地上限 10000 条。
     *
     * @param redis    Redis 客户端，可为 null（只用本地缓存）
     * @param localTtl 本地缓存时长
     */
    public SecurityUserCache(StringRedisTemplate redis, Duration localTtl) {
        this(redis, localTtl, DEFAULT_REDIS_TTL, DEFAULT_MAXIMUM_SIZE, Ticker.systemTicker());
    }

    SecurityUserCache(StringRedisTemplate redis, Duration localTtl, Duration redisTtl, long maximumSize, Ticker ticker) {
        this.redis = redis;
        this.redisTtl = Objects.requireNonNull(redisTtl, "redisTtl");
        this.local = Caffeine.newBuilder().ticker(ticker).expireAfterWrite(localTtl).maximumSize(maximumSize).build();
    }

    /**
     * 取用户在指定活动租户下的业务上下文。
     *
     * @param userId   用户 ID
     * @param tenantId 请求头指定的租户，为空表示默认租户
     * @param loader   缓存未命中时的加载器
     * @return 加载结果
     */
    public SecurityContextResult get(String userId, String tenantId, Supplier<SecurityContextResult> loader) {
        String localKey = userId + ":" + tenantPart(tenantId);
        SecurityContextResult cached = local.getIfPresent(localKey);
        if (cached != null) {
            return cached;
        }
        String redisKey = redisKey(userId, tenantId);
        if (redisKey != null) {
            SecurityContextResult shared = readRedis(redisKey);
            if (shared != null) {
                local.put(localKey, shared);
                return shared;
            }
        }
        SecurityContextResult loaded = Objects.requireNonNull(loader.get(), "loader result");
        if (loaded.getStatus() == SecurityContextResult.Status.OK) {
            local.put(localKey, loaded);
            if (redisKey != null) {
                writeRedis(redisKey, loaded);
            }
        }
        return loaded;
    }

    /**
     * 用户改动后换版本：本实例立刻失效，其他实例在本地缓存过期后失效。
     *
     * @param userId 用户 ID
     */
    public void bumpVersion(String userId) {
        local.asMap().keySet().removeIf(key -> key.startsWith(userId + ":"));
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().increment(VERSION_KEY_PREFIX + userId);
        } catch (RuntimeException ex) {
            log.warn("安全上下文版本自增失败，其他实例最多 {} 后失效：{}", local.policy().expireAfterWrite()
                    .map(policy -> policy.getExpiresAfter()).orElse(Duration.ZERO), ex.getMessage());
        }
    }

    private String redisKey(String userId, String tenantId) {
        if (redis == null) {
            return null;
        }
        try {
            String version = redis.opsForValue().get(VERSION_KEY_PREFIX + userId);
            return KEY_PREFIX + userId + ":" + (version == null ? "0" : version) + ":" + tenantPart(tenantId);
        } catch (RuntimeException ex) {
            log.warn("读取安全上下文版本失败，本次直接加载：{}", ex.getMessage());
            return null;
        }
    }

    private SecurityContextResult readRedis(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : JsonUtil.toBean(json, SecurityContextResult.class);
        } catch (RuntimeException ex) {
            log.warn("读取安全上下文缓存失败，本次直接加载：{}", ex.getMessage());
            return null;
        }
    }

    private void writeRedis(String key, SecurityContextResult result) {
        try {
            redis.opsForValue().set(key, JsonUtil.toJsonStr(result), redisTtl);
        } catch (RuntimeException ex) {
            log.warn("写入安全上下文缓存失败：{}", ex.getMessage());
        }
    }

    private static String tenantPart(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "_" : tenantId;
    }
}
