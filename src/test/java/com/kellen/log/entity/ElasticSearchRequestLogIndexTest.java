package com.kellen.log.entity;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Spring Data 元数据解析，防止共享 ES 时环境日志进入同名索引。 */
class ElasticSearchRequestLogIndexTest {

    @Test
    void personalEnvironmentUsesItsOwnRequestLogIndex() {
        assertThat(indexName(Map.of("elasticsearch.request-log-index", "github_dev_request_log")))
                .isEqualTo("github_dev_request_log");
    }

    @Test
    void independentApplicationContextsDoNotShareAnIndexOverride() {
        assertThat(indexName(Map.of("elasticsearch.request-log-index", "first_env_request_log")))
                .isEqualTo("first_env_request_log");
        assertThat(indexName(Map.of("elasticsearch.request-log-index", "second_env_request_log")))
                .isEqualTo("second_env_request_log");
    }

    @Test
    void existingConsumersKeepTheirIndexWhenNoOverrideIsConfigured() {
        assertThat(indexName(Map.of())).isEqualTo("request_log");
    }

    private String indexName(Map<String, Object> properties) {
        try (var context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", properties));
            context.refresh();
            var mapping = new SimpleElasticsearchMappingContext();
            mapping.setApplicationContext(context);
            return mapping.getRequiredPersistentEntity(ElasticSearchRequestLog.class)
                    .getIndexCoordinates().getIndexName();
        }
    }
}
