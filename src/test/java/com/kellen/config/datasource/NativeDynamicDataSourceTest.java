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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据源改用 dynamic-datasource 原生配置：服务 {@code application.yml} 的统一数据源块建出 master、gray 两个 Druid 池，
 * 并由 dynamic-datasource 按 Seata AT 包装；Seata 自动代理关闭，路由数据源本身不再被包一层。
 */
class NativeDynamicDataSourceTest {

    /** 各服务 application.yml 里与本测试相同的数据源块；地址、账号与口令在 Nacos。 */
    private static final String[] SERVICE_BLOCK = {
            "spring.datasource.dynamic.primary=master",
            "spring.datasource.dynamic.strict=true",
            "spring.datasource.dynamic.seata=true",
            "spring.datasource.dynamic.seata-mode=AT",
            "spring.datasource.dynamic.druid.initial-size=1",
            "spring.datasource.dynamic.druid.min-idle=1",
            "spring.datasource.dynamic.druid.max-wait=3000",
            "spring.datasource.dynamic.druid.connect-timeout=3000",
            "spring.datasource.dynamic.druid.socket-timeout=30000",
            "spring.datasource.dynamic.druid.validation-query=SELECT 1",
            "spring.datasource.dynamic.druid.validation-query-timeout=3",
            "spring.datasource.dynamic.druid.test-on-borrow=true",
            "spring.datasource.dynamic.druid.test-while-idle=true",
            "spring.datasource.dynamic.druid.keep-alive=true",
            "spring.datasource.dynamic.druid.time-between-eviction-runs-millis=30000",
            "spring.datasource.dynamic.druid.keep-alive-between-time-millis=60000",
            "spring.datasource.dynamic.datasource.master.type=com.alibaba.druid.pool.DruidDataSource",
            "spring.datasource.dynamic.datasource.master.druid.max-active=4",
            "spring.datasource.dynamic.datasource.gray.type=com.alibaba.druid.pool.DruidDataSource",
            "spring.datasource.dynamic.datasource.gray.druid.max-active=2",
            "seata.enable-auto-data-source-proxy=false"
    };

    /** 不可达的合成地址；不带 connectTimeout、socketTimeout，Druid 初始化时才不会拿 URL 里的秒数覆盖毫秒设置。 */
    private static final String UNREACHABLE = "jdbc:postgresql://127.0.0.1:1/fleet_pg_test_none";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DynamicDataSourceAutoConfiguration.class,
                    DruidDataSourceAutoConfigure.class, SeataDataSourceAutoConfiguration.class))
            .withPropertyValues(SERVICE_BLOCK);

    /** Seata 代理在构造时就要连库读元数据，不连库的用例只关掉这一项，其余与服务配置相同。 */
    @Test
    void nativePropertiesBuildMasterAndGrayPoolsWithConfiguredBudgets() {
        runner.withPropertyValues(nacosKeys(UNREACHABLE, "fixture", "synthetic-fixture"))
                .withPropertyValues("spring.datasource.dynamic.lazy=true", "spring.datasource.dynamic.seata=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Map<String, DataSource> pools = context.getBean(DynamicRoutingDataSource.class).getDataSources();
                    assertThat(pools).containsOnlyKeys("master", "gray");
                    assertBudget(realPool(pools.get("master")), 4);
                    assertBudget(realPool(pools.get("gray")), 2);
                });
    }

    @Test
    void noCustomDataSourceBeanRemains() {
        assertThatThrownBy(() -> Class.forName("com.kellen.config.datasource.DynamicDataSourceConfig"))
                .isInstanceOf(ClassNotFoundException.class);
        runner.withPropertyValues(nacosKeys(UNREACHABLE, "fixture", "synthetic-fixture"))
                .withPropertyValues("spring.datasource.dynamic.lazy=true", "spring.datasource.dynamic.seata=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(DataSource.class)).hasSize(1)
                            .allSatisfy((name, dataSource) -> assertThat(dataSource).isInstanceOf(DynamicRoutingDataSource.class));
                    assertThat(context.containsBean("master")).isFalse();
                    assertThat(context.containsBean("gray")).isFalse();
                });
    }

    /**
     * 与服务完全相同的配置（seata: true、AT），并像服务一样经 SpringApplication 启动：Seata 读配置依赖它在启动时注入的环境，
     * ApplicationContextRunner 不执行这一步。github-dev 关闭了 Seata（seata.enabled=false），这里保持一致。
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
            Map<String, Object> properties = new LinkedHashMap<>();
            for (String entry : SERVICE_BLOCK) {
                properties.put(entry.substring(0, entry.indexOf('=')), entry.substring(entry.indexOf('=') + 1));
            }
            for (String entry : nacosKeys(url, System.getenv("FLEET_PG_TEST_USERNAME"), System.getenv("FLEET_PG_TEST_PASSWORD"))) {
                properties.put(entry.substring(0, entry.indexOf('=')), entry.substring(entry.indexOf('=') + 1));
            }
            properties.put("seata.enabled", "false");
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

    private static String[] nacosKeys(String url, String username, String password) {
        List<String> keys = new ArrayList<>();
        for (String name : List.of("master", "gray")) {
            String prefix = "spring.datasource.dynamic.datasource." + name + ".";
            keys.add(prefix + "url=" + url);
            keys.add(prefix + "username=" + username);
            keys.add(prefix + "password=" + password);
            keys.add(prefix + "driver-class-name=org.postgresql.Driver");
        }
        return keys.toArray(String[]::new);
    }

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
