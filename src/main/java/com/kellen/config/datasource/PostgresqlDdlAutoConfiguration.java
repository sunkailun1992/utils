package com.kellen.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/** 在官方 DDL 自动配置之前注册 PostgreSQL 原子执行器，不依赖消费者组件扫描顺序。 */
@AutoConfiguration(
        beforeName = "com.baomidou.mybatisplus.autoconfigure.DdlAutoConfiguration",
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass({IDdl.class, DdlApplicationRunner.class})
public class PostgresqlDdlAutoConfiguration {

    /** 业务 IDdl 已由用户配置注册；保持官方 runner 类型，使默认实现可靠退让。 */
    @Bean
    @ConditionalOnBean(IDdl.class)
    @ConditionalOnMissingBean(DdlApplicationRunner.class)
    DdlApplicationRunner postgresqlDdlApplicationRunner(List<IDdl> ddlList) {
        return new PostgresqlDdlApplicationRunner(ddlList);
    }
}
