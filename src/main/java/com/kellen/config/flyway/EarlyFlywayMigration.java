package com.kellen.config.flyway;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 在创建任何 Bean 之前，按 {@code spring.flyway.*} 先执行一次 Flyway 迁移。
 *
 * <p>数据源块固定开启 Seata AT：dynamic-datasource 建池时就给每个池包 Seata 代理，代理初始化会连库检查
 * {@code undo_log}；Spring Boot 的 Flyway 却要先取到主数据源才迁移。新空库还没有 {@code undo_log}，启动因此失败。
 * 这里在 Bean 工厂后处理阶段单独建连接执行同一次迁移，之后 Spring Boot 自己的迁移只剩校验。</p>
 *
 * <p>只在 Spring Boot 本来就会在启动时迁移（有 {@link FlywayMigrationInitializer}、没有自定义
 * {@link FlywayMigrationStrategy}）且配置了 {@code spring.flyway.url} 时执行。只认服务 Flyway 块用到的键；
 * 出现其它 {@code spring.flyway.*} 键就停止启动，避免两次迁移的设置不一致。</p>
 *
 * @author 孙凯伦
 */
public class EarlyFlywayMigration implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(EarlyFlywayMigration.class);

    private static final ConfigurationPropertyName PREFIX = ConfigurationPropertyName.of("spring.flyway");

    /** 支持的键，统一写法（小写、去掉连字符）。 */
    private static final Set<String> SUPPORTED_KEYS = Set.of("enabled", "url", "user", "password", "locations",
            "baselineonmigrate", "baselineversion", "validateonmigrate", "cleandisabled");

    /** 与 Spring Boot 相同：迁移位置里的数据库厂商占位符。 */
    private static final String VENDOR_PLACEHOLDER = "{vendor}";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /** 排在其它 Bean 工厂后处理器之前，免得它们先取数据源。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!springBootMigratesAtStartup(beanFactory)) {
            return;
        }
        FlywayProperties properties = Binder.get(environment).bind("spring.flyway", FlywayProperties.class)
                .orElseGet(FlywayProperties::new);
        if (!StringUtils.hasText(properties.getUrl())) {
            log.info("未配置 spring.flyway.url，迁移留给 Spring Boot 在建数据源之后执行");
            return;
        }
        List<String> unsupported = unsupportedKeys();
        if (!unsupported.isEmpty()) {
            throw new IllegalStateException("提前迁移只支持 spring.flyway 的 " + SUPPORTED_KEYS
                    + "，不支持 " + unsupported + "；要用这些设置须先在 EarlyFlywayMigration 里补上");
        }
        log.info("建数据源之前先执行 Flyway 迁移（Seata AT 建池时要检查 undo_log）");
        Flyway.configure(beanFactory.getBeanClassLoader())
                .dataSource(properties.getUrl(), properties.getUser(), properties.getPassword())
                .locations(resolveLocations(properties).toArray(String[]::new))
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .baselineVersion(properties.getBaselineVersion())
                .validateOnMigrate(properties.isValidateOnMigrate())
                .cleanDisabled(properties.isCleanDisabled())
                .load()
                .migrate();
    }

    /** Spring Boot 在启动时按 {@code spring.flyway.*} 迁移：有迁移初始化器，迁移方式也没被自定义。 */
    private static boolean springBootMigratesAtStartup(ConfigurableListableBeanFactory beanFactory) {
        return beanFactory.getBeanNamesForType(FlywayMigrationInitializer.class, true, false).length > 0
                && beanFactory.getBeanNamesForType(FlywayMigrationStrategy.class, true, false).length == 0;
    }

    private List<String> unsupportedKeys() {
        Set<String> keys = new TreeSet<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source instanceof IterableConfigurationPropertySource iterable) {
                iterable.stream()
                        .filter(PREFIX::isAncestorOf)
                        .filter(name -> !SUPPORTED_KEYS.contains(
                                name.getElement(PREFIX.getNumberOfElements(), ConfigurationPropertyName.Form.UNIFORM)))
                        .forEach(name -> keys.add(name.toString()));
            }
        }
        return List.copyOf(keys);
    }

    /** 与 Spring Boot 相同：按 JDBC 地址认出的数据库替换 {@code {vendor}}，认不出时原样保留。 */
    private static List<String> resolveLocations(FlywayProperties properties) {
        DatabaseDriver driver = DatabaseDriver.fromJdbcUrl(properties.getUrl());
        if (driver == DatabaseDriver.UNKNOWN) {
            return properties.getLocations();
        }
        return properties.getLocations().stream()
                .map(location -> location.replace(VENDOR_PLACEHOLDER, driver.getId()))
                .toList();
    }
}
