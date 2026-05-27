package com.kellen.config.elasticsearch;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * @ClassName ElasticsearchConfig
 * @Description es配置
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/9/15 11:04 上午
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final ConfigurableApplicationContext applicationContext;

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
