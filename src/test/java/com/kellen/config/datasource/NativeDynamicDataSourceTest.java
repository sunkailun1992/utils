package com.kellen.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据源改用 dynamic-datasource 原生配置：服务 {@code application.yml} 的统一数据源块建出 master、gray 两个 Druid 池；
 * 启用 Seata 时由 dynamic-datasource 按 AT 包装，Seata 自动代理关闭，路由数据源本身不再被包一层。
 */
class NativeDynamicDataSourceTest {

    /** 不可达的合成地址；不带 connectTimeout、socketTimeout，Druid 初始化时才不会拿 URL 里的秒数覆盖毫秒设置。 */
    private static final String UNREACHABLE = "jdbc:postgresql://127.0.0.1:1/fleet_pg_test_none";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DynamicDataSourceAutoConfiguration.class,
                    DruidDataSourceAutoConfigure.class, SeataDataSourceAutoConfiguration.class))
            .withPropertyValues(ServiceDataSourceBlock.PROPERTIES);

    /** 与 github-dev 一样关闭 Seata：两个池不包 Seata 代理，也就不在建池时连库。 */
    @Test
    void nativePropertiesBuildMasterAndGrayPoolsWithConfiguredBudgets() {
        runner.withPropertyValues(ServiceDataSourceBlock.nacosKeys(UNREACHABLE, "fixture", "synthetic-fixture"))
                .withPropertyValues("spring.datasource.dynamic.lazy=true", "seata.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Map<String, DataSource> pools = context.getBean(DynamicRoutingDataSource.class).getDataSources();
                    assertThat(pools).containsOnlyKeys("master", "gray");
                    assertThat(pools).allSatisfy((name, dataSource) -> assertThat(((ItemDataSource) dataSource).getDataSource())
                            .as("Seata 关闭时不包代理").isSameAs(((ItemDataSource) dataSource).getRealDataSource()));
                    assertBudget(realPool(pools.get("master")), 4);
                    assertBudget(realPool(pools.get("gray")), 2);
                });
    }

    @Test
    void noCustomDataSourceBeanRemains() {
        assertThatThrownBy(() -> Class.forName("com.kellen.config.datasource.DynamicDataSourceConfig"))
                .isInstanceOf(ClassNotFoundException.class);
        runner.withPropertyValues(ServiceDataSourceBlock.nacosKeys(UNREACHABLE, "fixture", "synthetic-fixture"))
                .withPropertyValues("spring.datasource.dynamic.lazy=true", "seata.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(DataSource.class)).hasSize(1)
                            .allSatisfy((name, dataSource) -> assertThat(dataSource).isInstanceOf(DynamicRoutingDataSource.class));
                    assertThat(context.containsBean("master")).isFalse();
                    assertThat(context.containsBean("gray")).isFalse();
                });
    }

    /**
     * 启用 Seata 时（seata.enabled=true）由 dynamic-datasource 给两个池包 Seata AT 代理；库里须已有 undo_log。
     * 像服务一样经 SpringApplication 启动：Seata 读配置依赖它在启动时注入的环境，ApplicationContextRunner 不执行这一步。
     * 连专用测试库的随机独立 schema，结束后删除。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "FLEET_PG_TEST_URL", matches = "jdbc:postgresql:.*")
    void seataModeWrapsMasterAndGrayInSeataProxy() throws Exception {
        String base = System.getenv("FLEET_PG_TEST_URL");
        String schema = "native_ds_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(base)) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/test/undo_log.sql"));
        }
        try {
            String url = base + (base.contains("?") ? "&" : "?") + "currentSchema=" + schema;
            Map<String, Object> properties = ServiceDataSourceBlock.asMap(ServiceDataSourceBlock.PROPERTIES,
                    ServiceDataSourceBlock.nacosKeys(url, System.getenv("FLEET_PG_TEST_USERNAME"), System.getenv("FLEET_PG_TEST_PASSWORD")));
            properties.put("seata.enabled", "true");
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NativeDataSourceApplication.class)
                    .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                    .properties(properties).run()) {
                assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
                assertThat(context.getBean(DataSource.class)).isInstanceOf(DynamicRoutingDataSource.class);
                Map<String, DataSource> pools = context.getBean(DynamicRoutingDataSource.class).getDataSources();
                assertThat(pools).containsOnlyKeys("master", "gray").allSatisfy((name, dataSource) ->
                        assertThat(((ItemDataSource) dataSource).getDataSource()).isInstanceOf(SeataDataSourceProxy.class));
                assertThat(realPool(pools.get("master")).getMaxActive()).isEqualTo(4);
                assertThat(realPool(pools.get("gray")).getMaxActive()).isEqualTo(2);
                assertThat(new JdbcTemplate(context.getBean(DataSource.class)).queryForObject("SELECT 1", Integer.class))
                        .isEqualTo(1);
            }
        } finally {
            try (Connection connection = connection(base)) {
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    /** 只导入数据源相关的自动配置，其余按服务启动流程（环境后处理器、上下文初始化器）执行。 */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DynamicDataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            SeataDataSourceAutoConfiguration.class})
    static class NativeDataSourceApplication { }

    private static DruidDataSource realPool(DataSource dataSource) {
        assertThat(dataSource).isInstanceOf(ItemDataSource.class);
        DataSource real = ((ItemDataSource) dataSource).getRealDataSource();
        assertThat(real).isInstanceOf(DruidDataSource.class);
        return (DruidDataSource) real;
    }

    private static void assertBudget(DruidDataSource pool, int maxActive) throws Exception {
        assertThat(pool.getMaxActive()).isEqualTo(maxActive);
        assertThat(pool.getInitialSize()).isEqualTo(1);
        assertThat(pool.getMinIdle()).isEqualTo(1);
        assertThat(pool.getMaxWait()).isEqualTo(3000);
        assertThat(pool.getValidationQuery()).isEqualTo("SELECT 1");
        assertThat(pool.getValidationQueryTimeout()).isEqualTo(3);
        assertThat(pool.isTestOnBorrow()).isTrue();
        assertThat(pool.isTestWhileIdle()).isTrue();
        assertThat(pool.isKeepAlive()).isTrue();
        assertThat(pool.getTimeBetweenEvictionRunsMillis()).isEqualTo(30_000L);
        assertThat(pool.getKeepAliveBetweenTimeMillis()).isEqualTo(60_000L);
        pool.setInitialSize(0);
        pool.setMinIdle(0);
        pool.init();
        assertThat(pool.getConnectTimeout()).as("Druid 初始化后仍是毫秒设置").isEqualTo(3000);
        assertThat(pool.getSocketTimeout()).as("Druid 初始化后仍是毫秒设置").isEqualTo(30000);
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
}
