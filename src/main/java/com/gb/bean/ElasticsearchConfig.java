package com.gb.bean;

import lombok.Setter;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;

/**
 * @ClassName ElasticsearchConfig
 * @Description es配置
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/9/15 11:04 上午
 */
@Configuration
@Setter(onMethod_ = {@Autowired})
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {

    private ConfigurableApplicationContext applicationContext;

    @Override
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(applicationContext.getEnvironment().getProperty("elasticsearch.url"))
                .withBasicAuth(applicationContext.getEnvironment().getProperty("elasticsearch.username"),applicationContext.getEnvironment().getProperty("elasticsearch.password"))
                .build();
        return RestClients.create(clientConfiguration).rest();
    }
}
