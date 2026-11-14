package com.kellen.config.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.kellen.datapermission.DataPermissionProperties;
import com.kellen.security.config.TenantProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MyBatis-Plus 插件按官方文档的顺序注册：多租户、数据权限 → 分页、乐观锁 → SQL 规范检查、防全表更新删除。
 */
class MyBatisPlusInterceptorOrderTest {

    @Test
    void innerInterceptorsFollowOfficialOrder() {
        TenantProperties tenant = new TenantProperties();
        tenant.setEnabled(true);
        DataPermissionProperties dataPermission = new DataPermissionProperties();
        dataPermission.setEnabled(true);
        MybatisPlusSecurityProperties security = new MybatisPlusSecurityProperties();
        security.setIllegalSqlEnabled(true);
        security.setBlockAttackEnabled(true);

        List<Class<?>> order = new MyBatisPlusConfig(tenant, dataPermission, security)
                .mybatisPlusInterceptor().getInterceptors().stream()
                .<Class<?>>map(InnerInterceptor::getClass)
                .toList();

        assertThat(order).containsExactly(
                TenantLineInnerInterceptor.class,
                DataPermissionInterceptor.class,
                PaginationInnerInterceptor.class,
                OptimisticLockerInnerInterceptor.class,
                IllegalSQLInnerInterceptor.class,
                BlockAttackInnerInterceptor.class);
    }
}
