package com.kellen.config.mybatis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis-Plus SQL 安全插件配置。
 *
 * <p>用于统一控制非法 SQL 拦截和防全表更新删除拦截，默认开启以保护公共包消费者项目。</p>
 *
 * @author 孙凯伦
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.security")
public class MybatisPlusSecurityProperties {

    /**
     * 是否启用非法 SQL 拦截插件。
     */
    private boolean illegalSqlEnabled = true; // 默认启用 IllegalSQLInnerInterceptor，用于提前拦截高风险 SQL。

    /**
     * 是否启用防全表更新删除插件。
     */
    private boolean blockAttackEnabled = true; // 默认启用 BlockAttackInnerInterceptor，用于阻止无条件 update/delete。
}
