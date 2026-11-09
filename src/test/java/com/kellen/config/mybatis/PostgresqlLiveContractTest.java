package com.kellen.config.mybatis;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.ddl.DdlHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.datapermission.DataPermissionProperties;
import com.kellen.entity.EntityBase;
import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 显式专用PG测试库；拒绝业务库名，只写合成fixture，不读任何真实业务行。 */
@EnabledIfEnvironmentVariable(named = "FLEET_PG_TEST_URL", matches = "jdbc:postgresql:.*")
class PostgresqlLiveContractTest {
    private Connection connection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", System.getenv("FLEET_PG_TEST_USERNAME"));
        properties.setProperty("password", System.getenv("FLEET_PG_TEST_PASSWORD"));
        properties.setProperty("connectTimeout", "3");
        properties.setProperty("socketTimeout", "5");
        Connection connection = DriverManager.getConnection(System.getenv("FLEET_PG_TEST_URL"), properties);
        if (!connection.getCatalog().startsWith("fleet_pg_test_")) {
            connection.close();
            throw new IllegalStateException("dedicated fleet_pg_test_ database required");
        }
        return connection;
    }

    @Test void officialDdlHelperSelectsPgHistoryAndCommonScriptIsRepeatable() throws Exception {
        String script = "db/postgresql/common-infra-schema.sql";
        try (Connection connection = connection()) {
            for (int run = 0; run < 2; run++) {
                DdlHelper.runScript(null, connection, List.of(script), runner -> {
                    runner.setStopOnError(true);
                    runner.setLogWriter(null);
                    runner.setErrorLogWriter(null);
                }, true, (name, error) -> { throw new IllegalStateException("PG fixture DDL failed", error); });
            }
            try (var query = connection.prepareStatement("SELECT count(*) FROM public.ddl_history WHERE script = ?")) {
                query.setString(1, script);
                try (var rows = query.executeQuery()) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(1); }
            }
            String xid = "synthetic-" + UUID.randomUUID();
            LocalDateTime preciseTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123456000);
            try {
                try (var insert = connection.prepareStatement("""
                        INSERT INTO public.undo_log
                        (branch_id, xid, context, rollback_info, log_status, log_created, log_modified)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, 7L);
                    insert.setString(2, xid);
                    insert.setString(3, "synthetic");
                    insert.setBytes(4, new byte[] {1, 2, 3});
                    insert.setInt(5, 0);
                    insert.setTimestamp(6, Timestamp.valueOf(preciseTime));
                    insert.setTimestamp(7, Timestamp.valueOf(preciseTime));
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    try (var keys = insert.getGeneratedKeys()) { assertThat(keys.next()).isTrue(); assertThat(keys.getLong(1)).isPositive(); }
                }
                try (var select = connection.prepareStatement("SELECT rollback_info, log_created, log_modified FROM public.undo_log WHERE xid = ? AND branch_id = 7")) {
                    select.setString(1, xid);
                    try (var rows = select.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getBytes(1)).containsExactly(1, 2, 3);
                        assertThat(rows.getTimestamp(2).toLocalDateTime()).isEqualTo(preciseTime);
                        assertThat(rows.getTimestamp(3).toLocalDateTime()).isEqualTo(preciseTime);
                    }
                }
            } finally {
                try (var delete = connection.prepareStatement("DELETE FROM public.undo_log WHERE xid = ? AND branch_id = 7")) {
                    delete.setString(1, xid);
                    delete.executeUpdate();
                }
            }
        }
    }

    @Test void actualPgExecutesTypedTenantPaginationAndBooleanSoftDelete() throws Exception {
        String table = "utils_pg_fixture_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE public." + table + " (id varchar(64) PRIMARY KEY, tenant_id varchar(64) NOT NULL, is_delete boolean NOT NULL DEFAULT false)");
            try {
                try (var insert = connection.prepareStatement("INSERT INTO public." + table + " VALUES (?, ?, ?)")) {
                    for (int index = 0; index < 25; index++) {
                        insert.setString(1, "row-" + String.format("%02d", index));
                        insert.setString(2, "00100"); insert.setBoolean(3, index == 0); insert.executeUpdate();
                    }
                    insert.setString(1, "other-tenant"); insert.setString(2, "100"); insert.setBoolean(3, false); insert.executeUpdate();
                    insert.setString(1, "quoted-tenant"); insert.setString(2, "tenant-'a"); insert.setBoolean(3, false); insert.executeUpdate();
                }
                TenantProperties tenants = new TenantProperties(); tenants.setEnabled(true); tenants.setIgnoreWithoutTenant(false);
                MyBatisPlusConfig config = new MyBatisPlusConfig(tenants, new DataPermissionProperties(), new MybatisPlusSecurityProperties());
                MybatisConfiguration mybatis = new MybatisConfiguration();
                GlobalConfigUtils.setGlobalConfig(mybatis, new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
                String active = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(mybatis, "fixture"), Fixture.class).getLogicDeleteSql(false, true);
                var tenant = new TenantLineInnerInterceptor(config.tenantLineHandler());
                TenantContextHolder.setTenantId("00100");
                String sql = tenant.parserSingle("SELECT id FROM public." + table + " WHERE " + active + " ORDER BY id", null);
                Map<String, Object> arguments = Map.of("page", new Page<>(2, 5));
                var mapping = new MappedStatement.Builder(mybatis, "live.page", new StaticSqlSource(mybatis, sql), SqlCommandType.SELECT).build();
                var bound = mapping.getBoundSql(arguments);
                var pagination = config.mybatisPlusInterceptor().getInterceptors().stream().filter(PaginationInnerInterceptor.class::isInstance)
                        .map(PaginationInnerInterceptor.class::cast).findFirst().orElseThrow();
                pagination.beforeQuery(null, mapping, arguments, RowBounds.DEFAULT, null, bound);
                List<String> ids = new ArrayList<>();
                try (var query = connection.prepareStatement(bound.getSql())) {
                    for (int index = 0; index < bound.getParameterMappings().size(); index++)
                        query.setObject(index + 1, bound.getAdditionalParameter(bound.getParameterMappings().get(index).getProperty()));
                    try (var rows = query.executeQuery()) { while (rows.next()) ids.add(rows.getString(1)); }
                }
                assertThat(ids).containsExactly("row-06", "row-07", "row-08", "row-09", "row-10");
                TenantContextHolder.setTenantId("tenant-'a");
                try (var rows = statement.executeQuery(tenant.parserSingle("SELECT id FROM public." + table, null))) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("quoted-tenant"); assertThat(rows.next()).isFalse();
                }
            } finally {
                TenantContextHolder.clear();
                statement.execute("DROP TABLE public." + table);
            }
        }
    }

    @TableName("utils_pg_live_fixture")
    static class Fixture extends EntityBase { }
}
