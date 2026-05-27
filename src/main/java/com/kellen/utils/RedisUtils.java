package com.kellen.utils;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存工具类。
 *
 * <p>当前认证授权体系以 Spring Security 上下文、JWT Bearer 和用户上下文为准，
 * 本工具类只保留通用缓存读写能力，不再提供历史 token 请求头或 Redis token 用户读取能力。</p>
 *
 * @author 孙凯伦
 * @DateTime 2020/12/27  下午4:37
 * @email 376253703@qq.com
 */
public class RedisUtils {

    /**
     * 写入JSON字符串值。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param v                   待序列化缓存值
     */
    public static void add(StringRedisTemplate stringRedisTemplate, String k, Object v) {
        stringRedisTemplate.opsForValue().set(k, JsonUtil.json(v)); // 对象统一序列化为JSON字符串，避免Redis中混入JDK序列化格式。
    }

    /**
     * 写入原始字符串值。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param v                   字符串缓存值
     */
    public static void add(StringRedisTemplate stringRedisTemplate, String k, String v) {
        stringRedisTemplate.opsForValue().set(k, v); // 字符串值直接写入，避免二次JSON转义。
    }

    /**
     * 写入JSON字符串值并设置过期时间。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param v                   待序列化缓存值
     * @param timeout             过期时间
     * @param unit                过期时间单位
     */
    public static void add(StringRedisTemplate stringRedisTemplate, String k, Object v, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(k, JsonUtil.json(v)); // 先写入缓存值。
        stringRedisTemplate.expire(k, timeout, unit); // 再设置过期时间，保持旧调用语义。
    }

    /**
     * 写入原始字符串值并设置过期时间。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param v                   字符串缓存值
     * @param timeout             过期时间
     * @param unit                过期时间单位
     */
    public static void add(StringRedisTemplate stringRedisTemplate, String k, String v, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(k, v); // 字符串值直接写入。
        stringRedisTemplate.expire(k, timeout, unit); // 为当前键设置过期时间。
    }

    /**
     * 读取字符串值。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @return 缓存字符串值
     */
    public static String get(StringRedisTemplate stringRedisTemplate, String k) {
        return stringRedisTemplate.opsForValue().get(k); // 读取 Redis 字符串值，未命中时由 RedisTemplate 返回 null。
    }

    /**
     * 写入Hash结构。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param map                 Hash字段集合
     */
    public static void addMap(StringRedisTemplate stringRedisTemplate, String k, Map<String, Object> map) {
        for (String mapk : map.keySet()) { // 逐个字段处理 Hash 值格式。
            if (filter(mapk)) { // IP 和来源码等基础字符串字段保持原值。
                map.put(mapk, map.get(mapk)); // 保留可直接检索的原始值。
            } else { // 其他复杂对象字段统一JSON化。
                map.put(mapk, JsonUtil.json(map.get(mapk))); // 避免Hash值出现不一致的对象序列化格式。
            }
        }
        stringRedisTemplate.opsForHash().putAll(k, map); // 一次性写入整个 Hash，减少 Redis 往返次数。
    }

    /**
     * 写入Hash结构并设置过期时间。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param map                 Hash字段集合
     * @param timeout             过期时间
     * @param unit                过期时间单位
     */
    public static void addMap(StringRedisTemplate stringRedisTemplate, String k, Map<String, Object> map, long timeout, TimeUnit unit) {
        for (String mapk : map.keySet()) { // 逐个字段处理 Hash 值格式。
            if (filter(mapk)) { // IP 和来源码等基础字符串字段保持原值。
                map.put(mapk, map.get(mapk)); // 保留可直接检索的原始值。
            } else { // 其他复杂对象字段统一JSON化。
                map.put(mapk, JsonUtil.json(map.get(mapk))); // 避免Hash值出现不一致的对象序列化格式。
            }
        }
        stringRedisTemplate.opsForHash().putAll(k, map); // 一次性写入整个 Hash。
        stringRedisTemplate.expire(k, timeout, unit); // 为整个 Hash 键设置过期时间。
    }

    /**
     * 判断Hash字段是否应保留原始字符串值。
     *
     * @param value Hash字段名
     * @return true 表示保留原值，false 表示序列化为JSON
     */
    public static Boolean filter(String value) {
        return StringUtils.equals(value, "ip") || StringUtils.equals(value, "sourceCode"); // 旧 token 字段不再作为认证缓存特殊字段处理。
    }

    /**
     * 读取Hash字段值。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param k                   缓存键
     * @param mapK                Hash字段名
     * @return Hash字段值
     */
    public static Object getMap(StringRedisTemplate stringRedisTemplate, String k, String mapK) {
        return stringRedisTemplate.opsForHash().get(k, mapK); // 读取指定 Hash 字段，未命中时返回 null。
    }

    /**
     * 按匹配表达式删除缓存键。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param key                 Redis keys 匹配表达式
     */
    public static void deleteFuzzy(StringRedisTemplate stringRedisTemplate, String key) {
        Set<String> keys = stringRedisTemplate.keys(key); // 获取匹配表达式命中的 Redis 键集合。
        if (!CollectionUtils.isEmpty(keys)) {
            stringRedisTemplate.delete(keys); // 批量删除命中的缓存键。
        }
    }


    /**
     * 删除单个缓存键。
     *
     * @param stringRedisTemplate Redis字符串模板
     * @param key                 缓存键
     */
    public static void delete(StringRedisTemplate stringRedisTemplate, String key) {
        stringRedisTemplate.delete(key); // 删除指定 Redis 键。
    }


}
