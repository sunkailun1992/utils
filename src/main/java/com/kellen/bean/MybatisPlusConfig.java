package com.kellen.bean;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.kellen.utils.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2019-09-11  16:53
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class MybatisPlusConfig {

    private final TenantProperties tenantProperties;

    public MybatisPlusConfig(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    /**
     * 控制器
     * @return
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户
        if (tenantProperties.isEnabled()) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        }
        //分页
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        //乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenantId = TenantContextHolder.getTenantId();
                if (StringUtils.isNumeric(tenantId)) {
                    return new LongValue(tenantId);
                }
                return new StringValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return tenantProperties.getColumn();
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (TenantContextHolder.isIgnore()) {
                    return true;
                }
                if (StringUtils.isBlank(TenantContextHolder.getTenantId()) && tenantProperties.isIgnoreWithoutTenant()) {
                    return true;
                }
                return tenantProperties.getIgnoreTables().stream()
                        .filter(StringUtils::isNotBlank)
                        .map(table -> table.toLowerCase(Locale.ROOT))
                        .anyMatch(table -> table.equals(tableName.toLowerCase(Locale.ROOT)));
            }
        };
    }
}
