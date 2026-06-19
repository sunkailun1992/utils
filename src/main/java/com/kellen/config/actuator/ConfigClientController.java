package com.kellen.config.actuator;

import com.kellen.utils.redisson.LockUtil;
import com.kellen.utils.redisson.RedissonLocker;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2018/7/20  上午11:29
 * @email 376253703@qq.com
 *
 * @explain
 */
@Configuration
public class ConfigClientController {
    /**
     * redis
     */
    @Value("${redis.hostName}")
    private String redisHostName;
    @Value("${redis.port}")
    private Integer redisPort;
    @Value("${redis.database}")
    private Integer redisDatabase;
    @Value("${redis.password}")
    private String redisPassword;


    /**
     * redis数据源
     *
     * @param :
     * @return org.springframework.data.redis.core.RedisTemplate<java.lang.String, java.lang.Object>
     * @author 孙凯伦
     * @DateTime 2018/7/23  下午12:36
     * @email 376253703@qq.com
     *
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplateObject() {
        RedisTemplate<String, Object> redisTemplateObject = new RedisTemplate<>();
        redisTemplateObject.setConnectionFactory(redisConnectionFactory());
        redisTemplateObject.afterPropertiesSet();
        return redisTemplateObject;
    }

    /**
     * redis详细数据源
     *
     * @param :
     * @return org.springframework.data.redis.connection.RedisConnectionFactory
     * @author 孙凯伦
     * @DateTime 2018/7/23  下午12:36
     * @email 376253703@qq.com
     *
     */
    @Bean
    protected RedisConnectionFactory redisConnectionFactory() {
        /**
         * 设置链接地址
         */
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(redisHostName);
        redisStandaloneConfiguration.setPort(redisPort);
        redisStandaloneConfiguration.setDatabase(redisDatabase);
        redisStandaloneConfiguration.setPassword(RedisPassword.of(redisPassword));
        /**
         * 设置超时配置
         */
        JedisClientConfiguration.JedisClientConfigurationBuilder jedisClientConfiguration = JedisClientConfiguration.builder();
        jedisClientConfiguration.connectTimeout(Duration.ofMinutes(1));

        return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());
    }


    /**
     * RedissonClient,分布式锁配置
     *
     * @return
     */
    @Bean
    public RedissonClient redisson() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + redisHostName + ":" + redisPort);
        singleServerConfig.setDatabase(redisDatabase);
        //有密码
        if (redisPassword != null && !"".equals(redisPassword)) {
            singleServerConfig.setPassword(redisPassword);
        }
        return Redisson.create(config);
    }

    /**
     * 分布式锁初始化
     *
     * @param redissonClient
     * @return
     */
    @Bean
    public RedissonLocker redissonLocker(RedissonClient redissonClient) {
        RedissonLocker locker = new RedissonLocker(redissonClient);
        //设置LockUtil的锁处理对象
        LockUtil.setLocker(locker);
        return locker;
    }

}
