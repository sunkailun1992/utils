package com.kellen.config.flyway;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 空库首次启动时 Flyway 必须先于 MyBatis 访问数据库：初始化阶段经 Mapper 查询的表只由迁移创建。
 * 只连 {@code fleet_pg_test_} 开头的专用库，在随机独立 schema 里执行，结束后删除该 schema。
 */
@EnabledIfEnvironmentVariable(named = "FLEET_PG_TEST_URL", matches = "jdbc:postgresql:.*")
class FlywayMybatisOrderingLiveTest {

    @Test
    void flywayMigratesBeforeMybatisQueriesDuringStartup() throws Exception {
        String schema = "ordering_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("FLEET_PG_TEST_URL");
        String isolatedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try (Connection connection = connection(url)) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
        }
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                            FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class))
                    .withUserConfiguration(ProbeConfiguration.class)
                    .withPropertyValues("spring.datasource.url=" + isolatedUrl,
                            "spring.datasource.username=" + System.getenv("FLEET_PG_TEST_USERNAME"),
                            "spring.datasource.password=" + System.getenv("FLEET_PG_TEST_PASSWORD"),
                            "spring.datasource.hikari.maximum-pool-size=2",
                            "spring.flyway.locations=classpath:db/test/ordering")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(OrderingProbe.class).rowsAtStartup).isZero();
                        var beans = context.getBeanFactory();
                        assertThat(beans.getBeanNamesForType(SqlSessionTemplate.class)).isNotEmpty().allSatisfy(name ->
                                assertThat(beans.getBeanDefinition(name).getDependsOn())
                                        .as("MyBatis-Plus 顺序探测器在 Boot 4 下让 Mapper 用的 SqlSessionTemplate 等 Flyway")
                                        .contains("flywayInitializer"));
                    });
            try (Connection connection = connection(url);
                 var rows = connection.createStatement().executeQuery(
                         "SELECT version FROM " + schema + ".flyway_schema_history WHERE success")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("1");
            }
        } finally {
            try (Connection connection = connection(url)) {
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private static Connection connection(String url) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", System.getenv("FLEET_PG_TEST_USERNAME"));
        properties.setProperty("password", System.getenv("FLEET_PG_TEST_PASSWORD"));
        properties.setProperty("connectTimeout", "3");
        properties.setProperty("socketTimeout", "10");
        Connection connection = DriverManager.getConnection(url, properties);
        if (!connection.getCatalog().startsWith("fleet_pg_test_")) {
            connection.close();
            throw new IllegalStateException("dedicated fleet_pg_test_ database required");
        }
        return connection;
    }

    @Mapper
    interface OrderingProbeMapper {
        @Select("SELECT count(*) FROM ordering_probe")
        long countRows();
    }

    /** 初始化回调里查询迁移创建的表；没有迁移在前时上下文启动失败。 */
    static final class OrderingProbe implements InitializingBean {
        private final OrderingProbeMapper mapper;
        private long rowsAtStartup = -1;

        OrderingProbe(OrderingProbeMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void afterPropertiesSet() {
            rowsAtStartup = mapper.countRows();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = FlywayMybatisOrderingLiveTest.class, annotationClass = Mapper.class)
    static class ProbeConfiguration {
        @Bean
        OrderingProbe orderingProbe(OrderingProbeMapper mapper) {
            return new OrderingProbe(mapper);
        }
    }
}
