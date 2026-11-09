package com.kellen.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 只绑定合成连接属性，不建立网络连接，也不启动Seata/Nacos。 */
class PostgresqlDataSourceContractTest {
    private DynamicDataSourceConfig bind(String prefix) {
        return bind(prefix, Map.of());
    }

    private DynamicDataSourceConfig bind(String prefix, Map<String, String> overrides) {
        MockEnvironment environment = new MockEnvironment();
        for (String name : new String[] {prefix, prefix + "-gray"}) {
            Map.of("url", "jdbc:postgresql://127.0.0.1:1/synthetic_fixture", "username", "fixture",
                    "password", "synthetic-password", "driverClassName", "org.postgresql.Driver")
                    .forEach((key, value) -> environment.setProperty(name + "." + key, value));
        }
        overrides.forEach(environment::setProperty);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(environment::resolveRequiredPlaceholders);
        var processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(beans);
        beans.addBeanPostProcessor(processor);
        beans.registerBeanDefinition("dataSourceConfiguration", new RootBeanDefinition(DynamicDataSourceConfig.class));
        return beans.getBean(DynamicDataSourceConfig.class);
    }

    @Test void postgresqlNamespaceBindsBothExistingRouteAliasesWithoutMysqlKeys() {
        DynamicDataSourceConfig config = bind("postgresql");
        try (DruidDataSource master = config.master(); DruidDataSource gray = (DruidDataSource) config.gray()) {
            assertThat(master.getUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:1/synthetic_fixture");
            assertThat(gray.getUrl()).isEqualTo(master.getUrl());
            assertThat(master.getDriverClassName()).isEqualTo("org.postgresql.Driver");
            assertThat(gray.getDriverClassName()).isEqualTo(master.getDriverClassName());
            assertThat(master.getUsername()).isEqualTo("fixture");
            assertThat(master.getValidationQuery()).isEqualTo("SELECT 1");
            assertThat(master.isTestOnBorrow()).isTrue();
        }
    }

    @Test void legacyMysqlNamespaceCannotSilentlyRemainTheActiveDatabase() {
        assertThatThrownBy(() -> bind("mysql")).isInstanceOf(RuntimeException.class);
    }

    @Test void mysqlUrlIsRejectedBeforeAnyConnectionOrCredentialLogging() {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "url", "jdbc:mysql://127.0.0.1:1/fixture");
        ReflectionTestUtils.setField(config, "driverClassName", "org.postgresql.Driver");
        ReflectionTestUtils.setField(config, "password", "synthetic-secret-not-in-errors");
        assertThatThrownBy(config::master).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("synthetic-secret").hasMessageNotContaining("127.0.0.1");
    }

    @Test void publishedRuntimeProvidesPostgresqlJdbcDriverWithoutOpeningAConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        assertThat(DriverManager.getDriver("jdbc:postgresql://127.0.0.1:1/fixture").getClass().getName())
                .isEqualTo("org.postgresql.Driver");
    }

    @Test void separatePoolBudgetsKeepOneServiceWithinSixBusinessConnections() {
        DynamicDataSourceConfig config = bind("postgresql");
        try (DruidDataSource master = config.master(); DruidDataSource gray = (DruidDataSource) config.gray()) {
            assertThat(master.getMaxActive()).isEqualTo(4);
            assertThat(gray.getMaxActive()).isEqualTo(2);
            assertThat(master.getInitialSize()).isEqualTo(1);
            assertThat(gray.getInitialSize()).isEqualTo(1);
            assertThat(master.getMinIdle()).isEqualTo(1);
            assertThat(gray.getMinIdle()).isEqualTo(1);
            assertThat(master.getMaxWait()).isEqualTo(3000);
            assertThat(gray.getMaxWait()).isEqualTo(3000);
        }
    }

    @Test void combinedPoolBudgetCannotExceedSharedPostgresqlCapacity() {
        var config = bind("postgresql", Map.of("utils.datasource.pool.master.max-active", "5",
                "utils.datasource.pool.gray.max-active", "2"));
        assertThatThrownBy(config::master).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void minimumIdleCannotExceedEitherPoolLimit() {
        var config = bind("postgresql", Map.of("utils.datasource.health.min-idle", "3"));
        assertThatThrownBy(config::master).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void zeroPoolWaitCannotSilentlyBecomeAnUnboundedCheckout() {
        var config = bind("postgresql", Map.of("utils.datasource.pool.max-wait-millis", "0"));
        assertThatThrownBy(config::master).isInstanceOf(IllegalArgumentException.class);
    }
}
