package com.kellen.config.datasource;

import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新空库首次启动：表与 Seata 的 {@code undo_log} 只由服务自己的 Flyway 迁移创建。
 *
 * <p>Spring Boot 建 Flyway 时会先取主数据源；dynamic-datasource 包 Seata AT 代理时会连库检查 {@code undo_log}，
 * 表不存在就启动失败。所以数据源块的 {@code seata} 跟随 {@code seata.enabled}：Seata 关闭时（github-dev 现状）
 * 不包代理，空库可以由 Flyway 建表；启用 Seata 前库里必须已有 {@code undo_log}。</p>
 *
 * <p>只连 {@code fleet_pg_test_} 开头的专用库，在随机独立 schema 里执行，结束后删除该 schema。</p>
 */
@EnabledIfEnvironmentVariable(named = "FLEET_PG_TEST_URL", matches = "jdbc:postgresql:.*")
class FreshDatabaseStartupLiveTest {

    @Test
    void emptyDatabaseStartsAndFlywayCreatesTablesWhileSeataIsOff() throws Exception {
        String base = System.getenv("FLEET_PG_TEST_URL");
        String schema = createSchema(base);
        try (ConfigurableApplicationContext context = start(base, schema, false)) {
            assertThat(context.getBean(FreshProbe.class).rowsAtStartup).isZero();
            try (Connection connection = connection(base);
                 var rows = connection.createStatement().executeQuery(
                         "SELECT version FROM " + schema + ".flyway_schema_history WHERE success ORDER BY installed_rank")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("1");
            }
        } finally {
            dropSchema(base, schema);
        }
    }

    /** 说明为什么要跟随 Seata 开关：Seata 开启时代理先于 Flyway 检查 undo_log，空库无法启动。 */
    @Test
    void emptyDatabaseCannotStartWhileSeataProxiesBeforeFlyway() throws Exception {
        String base = System.getenv("FLEET_PG_TEST_URL");
        String schema = createSchema(base);
        try {
            assertThatThrownBy(() -> start(base, schema, true).close())
                    .rootCause().hasMessageContaining("undo_log table not exist");
        } finally {
            dropSchema(base, schema);
        }
    }

    private static ConfigurableApplicationContext start(String base, String schema, boolean seataEnabled) {
        String url = base + (base.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        Map<String, Object> properties = ServiceDataSourceBlock.asMap(ServiceDataSourceBlock.PROPERTIES,
                ServiceDataSourceBlock.FLYWAY, ServiceDataSourceBlock.nacosKeys(url,
                        System.getenv("FLEET_PG_TEST_USERNAME"), System.getenv("FLEET_PG_TEST_PASSWORD")));
        properties.put("spring.flyway.locations", "classpath:db/test/fresh");
        properties.put("seata.enabled", String.valueOf(seataEnabled));
        return new SpringApplicationBuilder(FreshApplication.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                .properties(properties).run();
    }

    private static String createSchema(String base) throws Exception {
        String schema = "fresh_db_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(base)) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
        }
        return schema;
    }

    private static void dropSchema(String base, String schema) throws Exception {
        try (Connection connection = connection(base)) {
            connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
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
    interface FreshProbeMapper {
        @Select("SELECT count(*) FROM fresh_probe")
        long countRows();
    }

    /** 初始化回调里查询迁移创建的表。 */
    static final class FreshProbe implements InitializingBean {
        private final FreshProbeMapper mapper;
        private long rowsAtStartup = -1;

        FreshProbe(FreshProbeMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void afterPropertiesSet() {
            rowsAtStartup = mapper.countRows();
        }
    }

    /** 按服务启动流程，只导入数据源、Flyway、MyBatis-Plus 与 Seata 数据源的自动配置。 */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DynamicDataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class, SeataDataSourceAutoConfiguration.class})
    @MapperScan(basePackageClasses = FreshDatabaseStartupLiveTest.class, annotationClass = Mapper.class)
    static class FreshApplication {
        @Bean
        FreshProbe freshProbe(FreshProbeMapper mapper) {
            return new FreshProbe(mapper);
        }
    }
}
