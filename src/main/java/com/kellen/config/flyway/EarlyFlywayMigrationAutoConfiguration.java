package com.kellen.config.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 注册 {@link EarlyFlywayMigration}：新空库首次启动时，在数据源包 Seata 代理之前先完成迁移。
 *
 * @author 孙凯伦
 */
@AutoConfiguration
@ConditionalOnClass(Flyway.class)
public class EarlyFlywayMigrationAutoConfiguration {

    /**
     * 静态方法注册：Bean 工厂后处理器要早于其它 Bean 创建，不能依赖本配置类的实例。
     *
     * @return 提前迁移
     */
    @Bean
    public static EarlyFlywayMigration earlyFlywayMigration() {
        return new EarlyFlywayMigration();
    }
}
