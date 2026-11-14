package com.kellen.config.json;

import com.kellen.utils.json.JsonUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * 让 {@link JsonUtil} 改用 Spring Boot 配好的 {@link JsonMapper}（芋道 JsonUtils 的做法）；
 * 容器里没有唯一的 {@link JsonMapper} 时保持默认配置。
 *
 * @author 孙凯伦
 */
@AutoConfiguration
public class JsonUtilAutoConfiguration {

    /**
     * 所有单例创建完后把 Spring Boot 的映射器交给 {@link JsonUtil}。
     *
     * @param jsonMapper Spring Boot 配好的映射器
     * @return 初始化回调
     */
    @Bean
    public SmartInitializingSingleton jsonUtilInitializer(ObjectProvider<JsonMapper> jsonMapper) {
        return () -> jsonMapper.ifUnique(JsonUtil::init);
    }
}
