package com.kellen.config.redis;

import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;


/**
 *
 * @author 孙凯伦
 * 
 * @email: 376253703@qq.com
 * @description: Redis缓存配置
 * @date: 2022/1/18 10:26 AM
 *
 */
@Configuration
public class RedisCacheConfig {
    /**
     * 缓存空间
     */
    @Value("${redis.cache.cache-names}")
    private String cacheNames;

    /**
     * 配置缓存管理器
     * @param factory Redis 线程安全连接工厂
     * @return 缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = Maps.newHashMap();
        String [] cacheNamesList = cacheNames.split(",");
        for (String name : cacheNamesList) {
            String cache = name.split("-")[0];
            String expireTime = name.split("-")[1];
            RedisCacheConfiguration cacheConfig = null;
            if("0".equals(expireTime)){
                // 生成两套默认配置，通过 Config 对象即可对缓存进行自定义配置
                cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                        // 设置缓存前缀
                        .computePrefixWith(cacheName -> "cache:" + cache + ":")
                        // 禁止缓存 null 值
                        .disableCachingNullValues()
                        // 设置 key 序列化
                        .serializeKeysWith(keyPair())
                        // 设置 value 序列化
                        .serializeValuesWith(valuePair());
            }else{
                // 生成两套默认配置，通过 Config 对象即可对缓存进行自定义配置
                cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                        // 设置过期时间，分钟
                        .entryTtl(Duration.ofMinutes(Integer.valueOf(expireTime)))
                        // 设置缓存前缀
                        .computePrefixWith(cacheName -> "cache:" + cache + ":")
                        // 禁止缓存 null 值
                        .disableCachingNullValues()
                        // 设置 key 序列化
                        .serializeKeysWith(keyPair())
                        // 设置 value 序列化
                        .serializeValuesWith(valuePair());
            }
            cacheConfigurations.put(cache,cacheConfig);
        }
        // 返回 Redis 缓存管理器
        return RedisCacheManager.builder(factory)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 配置键序列化
     * @return StringRedisSerializer
     */
    private RedisSerializationContext.SerializationPair<String> keyPair() {
        return RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());
    }

    /**
     * 配置值序列化，使用 GenericJackson2JsonRedisSerializer 替换默认序列化
     * @return GenericJackson2JsonRedisSerializer
     */
    private RedisSerializationContext.SerializationPair<Object> valuePair() {
        return RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());
    }

}
