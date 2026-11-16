package com.kellen.config.flyway;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提前迁移只在 Spring Boot 本来就会在启动时迁移、且配置了 {@code spring.flyway.url} 时执行；
 * 只认服务 Flyway 块用到的键，出现其它 {@code spring.flyway.*} 键就停止启动。
 */
class EarlyFlywayMigrationTest {

    /** 不可达的合成地址：真去连库会立即被拒绝。 */
    private static final String UNREACHABLE = "jdbc:postgresql://127.0.0.1:1/fleet_pg_test_none";

    @Test
    void registeredAsAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(EarlyFlywayMigrationAutoConfiguration.class.getName());
    }

    @Test
    void migratesWhenSpringBootMigratesAtStartup() {
        assertThatThrownBy(() -> postProcess(serviceFlywayBlock(), FlywayMigrationInitializer.class))
                .as("按服务的 Flyway 块真去连库，不可达地址连接失败")
                .isInstanceOf(FlywayException.class);
    }

    @Test
    void skipsWhenSpringBootDoesNotMigrateAtStartup() {
        assertThatCode(() -> postProcess(serviceFlywayBlock())).doesNotThrowAnyException();
    }

    @Test
    void customMigrationStrategyKeepsControl() {
        assertThatCode(() -> postProcess(serviceFlywayBlock(), FlywayMigrationInitializer.class,
                FlywayMigrationStrategy.class)).doesNotThrowAnyException();
    }

    @Test
    void withoutFlywayUrlLeavesMigrationToSpringBoot() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.flyway.baseline-on-migrate", "true");
        assertThatCode(() -> postProcess(environment, FlywayMigrationInitializer.class)).doesNotThrowAnyException();
    }

    @Test
    void unsupportedFlywaySettingStopsStartupBeforeConnecting() {
        MockEnvironment environment = serviceFlywayBlock().withProperty("spring.flyway.table", "custom_history");
        assertThatThrownBy(() -> postProcess(environment, FlywayMigrationInitializer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.table");
    }

    /** 与服务 application.yml 的 Flyway 块相同的键。 */
    private static MockEnvironment serviceFlywayBlock() {
        return new MockEnvironment()
                .withProperty("spring.flyway.locations", "classpath:db/test/fresh")
                .withProperty("spring.flyway.baseline-on-migrate", "true")
                .withProperty("spring.flyway.baseline-version", "1")
                .withProperty("spring.flyway.validate-on-migrate", "true")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("spring.flyway.url", UNREACHABLE)
                .withProperty("spring.flyway.user", "fixture")
                .withProperty("spring.flyway.password", "synthetic-fixture");
    }

    /** 容器里只登记给定类型的 Bean 定义（不创建实例），再执行提前迁移。 */
    private static void postProcess(MockEnvironment environment, Class<?>... beanTypes) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        for (Class<?> type : beanTypes) {
            beanFactory.registerBeanDefinition(type.getSimpleName(), new RootBeanDefinition(type));
        }
        EarlyFlywayMigration migration = new EarlyFlywayMigration();
        migration.setEnvironment(environment);
        migration.postProcessBeanFactory(beanFactory);
    }
}
