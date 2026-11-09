package com.kellen.config.mybatis;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.datapermission.DataPermissionProperties;
import com.kellen.entity.EntityBase;
import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实际生成SQL，防止PG把varchar租户与数字比较、分页方言错误或Boolean逻辑删除写成整数。 */
class PostgresqlSqlContractTest {
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    private MyBatisPlusConfig configuration() {
        TenantProperties tenants = new TenantProperties();
        tenants.setEnabled(true);
        tenants.setIgnoreWithoutTenant(false);
        return new MyBatisPlusConfig(tenants, new DataPermissionProperties(), new MybatisPlusSecurityProperties());
    }

    @Test void numericLookingTenantRetainsVarcharTypeAndLeadingZeros() {
        TenantContextHolder.setTenantId("00100");
        var interceptor = new TenantLineInnerInterceptor(configuration().tenantLineHandler());
        String sql = interceptor.parserSingle("SELECT id FROM auth_user", null);
        assertThat(sql).contains("tenant_id = '00100'");
    }

    @Test void tenantStringLiteralEscapesQuotesWithoutChangingItsValue() {
        TenantContextHolder.setTenantId("tenant-'a");
        var interceptor = new TenantLineInnerInterceptor(configuration().tenantLineHandler());
        String sql = interceptor.parserSingle("SELECT id FROM auth_user", null);
        assertThat(sql).contains("tenant_id = 'tenant-''a'");
    }

    @Test void missingTenantFailsClosedWhenTenantFilteringIsRequired() {
        var interceptor = new TenantLineInnerInterceptor(configuration().tenantLineHandler());
        assertThatThrownBy(() -> interceptor.parserSingle("SELECT id FROM auth_user", null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void actualPaginationUsesPostgresqlLimitOffsetOrder() {
        var interceptor = configuration().mybatisPlusInterceptor().getInterceptors().stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast).findFirst().orElseThrow();
        MybatisConfiguration config = new MybatisConfiguration();
        Map<String, Object> arguments = Map.of("page", new Page<>(3, 10));
        String original = "SELECT id FROM auth_user ORDER BY id";
        MappedStatement statement = new MappedStatement.Builder(config, "fixture.page",
                new StaticSqlSource(config, original), SqlCommandType.SELECT).build();
        BoundSql bound = statement.getBoundSql(arguments);
        interceptor.beforeQuery(null, statement, arguments, RowBounds.DEFAULT, null, bound);
        assertThat(bound.getSql()).isEqualTo(original + " LIMIT ? OFFSET ?");
        assertThat(bound.getParameterMappings()).hasSize(2);
        assertThat(bound.getAdditionalParameter(bound.getParameterMappings().get(0).getProperty())).isEqualTo(10L);
        assertThat(bound.getAdditionalParameter(bound.getParameterMappings().get(1).getProperty())).isEqualTo(20L);
    }

    @Test void inheritedBooleanSoftDeleteGeneratesPostgresqlBooleanLiterals() {
        MybatisConfiguration config = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(config, new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        var info = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config, "fixture"), Fixture.class);
        assertThat(info.getLogicDeleteSql(false, true).replace(" ", "")).isEqualTo("is_delete=false");
        assertThat(info.getLogicDeleteFieldInfo().getLogicDeleteValue()).isEqualTo("true");
    }

    @TableName("pg_sql_fixture")
    static class Fixture extends EntityBase { }
}
