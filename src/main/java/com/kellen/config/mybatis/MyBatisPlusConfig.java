package com.kellen.config.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.kellen.datapermission.DataPermissionProperties;
import com.kellen.datapermission.DataPermissionSqlHandler;
import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * MyBatis-Plus 通用插件配置。
 *
 * @author 孙凯伦
 */
@Configuration
@EnableConfigurationProperties({TenantProperties.class, DataPermissionProperties.class})
public class MyBatisPlusConfig {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 数据权限配置属性。
     */
    private final DataPermissionProperties dataPermissionProperties;

    /**
     * 构造 MyBatis-Plus 配置。
     *
     * @param tenantProperties          租户配置属性
     * @param dataPermissionProperties 数据权限配置属性
     */
    public MyBatisPlusConfig(TenantProperties tenantProperties, DataPermissionProperties dataPermissionProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供租户插件读取。
        this.dataPermissionProperties = dataPermissionProperties; // 保存数据权限配置，供数据权限插件读取。
    }

    /**
     * 创建 MyBatis-Plus 拦截器链。
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); // 创建 MyBatis-Plus 统一拦截器容器。
        if (tenantProperties.isEnabled()) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler())); // 开启租户插件后自动为 SQL 拼接租户条件。
        }
        if (dataPermissionProperties.isEnabled()) {
            interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionSqlHandler(dataPermissionProperties))); // 开启数据权限插件后自动为 SQL 拼接部门或本人条件。
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); // 注册 MySQL 分页插件。
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor()); // 注册 @Version 乐观锁插件。
        return interceptor; // 返回完整插件链。
    }

    /**
     * 创建租户 SQL 处理器。
     *
     * @return 租户 SQL 处理器
     */
    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            /**
             * 获取当前租户表达式。
             *
             * @return 当前租户 SQL 表达式
             */
            @Override
            public Expression getTenantId() {
                String tenantId = TenantContextHolder.getTenantId(); // 从租户上下文读取租户ID。
                if (StringUtils.isNumeric(tenantId)) {
                    return new LongValue(tenantId); // 纯数字租户使用数字表达式，避免 SQL 类型不匹配。
                }
                return new StringValue(tenantId); // 非数字租户使用字符串表达式。
            }

            /**
             * 获取数据库租户字段名。
             *
             * @return 租户字段名
             */
            @Override
            public String getTenantIdColumn() {
                return tenantProperties.getColumn(); // 使用配置中的数据库租户字段。
            }

            /**
             * 判断当前表是否忽略租户条件。
             *
             * @param tableName 数据库表名
             * @return true 表示忽略租户条件
             */
            @Override
            public boolean ignoreTable(String tableName) {
                if (TenantContextHolder.isIgnore()) {
                    return true; // 当前线程显式忽略租户时直接跳过租户条件。
                }
                if (StringUtils.isBlank(TenantContextHolder.getTenantId()) && tenantProperties.isIgnoreWithoutTenant()) {
                    return true; // 没有租户上下文且配置允许忽略时跳过租户条件。
                }
                return tenantProperties.getIgnoreTables().stream()
                        .filter(StringUtils::isNotBlank) // 过滤空表名配置。
                        .map(table -> table.toLowerCase(Locale.ROOT)) // 忽略大小写比较。
                        .anyMatch(table -> table.equals(tableName.toLowerCase(Locale.ROOT))); // 命中忽略表时跳过租户条件。
            }
        };
    }
}
