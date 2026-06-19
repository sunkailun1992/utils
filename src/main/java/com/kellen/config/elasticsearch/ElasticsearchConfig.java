package com.kellen.config.elasticsearch;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * Elasticsearch 客户端配置。
 *
 * <p>从环境配置 {@code elasticsearch.url/username/password} 构建带 Basic 认证的 ES 客户端。</p>
 *
 * @author 孙凯伦
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final ConfigurableApplicationContext applicationContext;

    /**
     * @param applicationContext 用于读取 ES 连接配置的应用上下文
     */
    public ElasticsearchConfig(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(applicationContext.getEnvironment().getProperty("elasticsearch.url"))
                .withBasicAuth(applicationContext.getEnvironment().getProperty("elasticsearch.username"), applicationContext.getEnvironment().getProperty("elasticsearch.password"))
                .build();
    }
}
