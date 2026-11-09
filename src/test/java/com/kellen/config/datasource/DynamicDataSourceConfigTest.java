package com.kellen.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 验证公共动态数据源默认启用失效连接检测和空闲保活。
 */
class DynamicDataSourceConfigTest {

    /**
     * 数据库关闭空闲连接后，连接池必须在借出前识别坏连接，避免首次业务请求失败。
     */
    @Test
    void shouldEnableConnectionHealthChecksForMasterDataSource() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "url", "jdbc:postgresql://127.0.0.1:1/synthetic_fixture");
        ReflectionTestUtils.setField(config, "username", "test");
        ReflectionTestUtils.setField(config, "password", "test");
        ReflectionTestUtils.setField(config, "driverClassName", "org.postgresql.Driver");

        try (DruidDataSource dataSource = config.master()) {
            assertThat(dataSource.getValidationQuery()).isEqualTo("SELECT 1");
            assertThat(dataSource.getValidationQueryTimeout()).isEqualTo(3);
            assertThat(dataSource.isTestOnBorrow()).isTrue();
            assertThat(dataSource.isTestWhileIdle()).isTrue();
            assertThat(dataSource.isKeepAlive()).isTrue();
            assertThat(dataSource.getTimeBetweenEvictionRunsMillis()).isEqualTo(30_000L);
            assertThat(dataSource.getKeepAliveBetweenTimeMillis()).isEqualTo(60_000L);
            assertThat(dataSource.getMinIdle()).isEqualTo(1);
            assertThatCode(() -> initializeWithoutNetwork(dataSource)).doesNotThrowAnyException();
        }
    }

    /**
     * 即使外部配置把保活间隔设成等于或小于检测间隔，也不能让 Druid 初始化失败。
     */
    @Test
    void shouldNormalizeInvalidKeepAliveInterval() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "url", "jdbc:postgresql://127.0.0.1:1/synthetic_fixture");
        ReflectionTestUtils.setField(config, "username", "test");
        ReflectionTestUtils.setField(config, "password", "test");
        ReflectionTestUtils.setField(config, "driverClassName", "org.postgresql.Driver");
        ReflectionTestUtils.setField(config, "evictionRunIntervalMillis", 60_000L);
        ReflectionTestUtils.setField(config, "keepAliveIntervalMillis", 30_000L);

        try (DruidDataSource dataSource = config.master()) {
            assertThat(dataSource.getTimeBetweenEvictionRunsMillis()).isEqualTo(60_000L);
            assertThat(dataSource.getKeepAliveBetweenTimeMillis()).isGreaterThan(60_000L);
            assertThatCode(() -> initializeWithoutNetwork(dataSource)).doesNotThrowAnyException();
        }
    }

    /** 隔离池初始化参数检查，不让健康配置单测访问任何数据库。初始容量由独立契约测试验证。 */
    private void initializeWithoutNetwork(DruidDataSource dataSource) throws Exception {
        dataSource.setInitialSize(0);
        dataSource.setMinIdle(0);
        dataSource.init();
    }
}
