package com.kellen.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** 只访问显式 NAS 专用测试库；慢查询只读，且每次只借出一个连接。 */
@EnabledIfEnvironmentVariable(named = "FLEET_PG_TEST_URL", matches = "jdbc:postgresql:.*")
class PostgresqlDruidTimeoutLiveTest {
    @Test
    void physicalPgConnectionRetainsThirtySecondsAndCompletesA120MillisecondRead() throws Exception {
        String raw = System.getenv("FLEET_PG_TEST_URL");
        String base = raw.split("\\?", 2)[0];
        String database = URI.create(base.substring("jdbc:".length())).getPath().substring(1);
        if (!database.startsWith("fleet_pg_test_") || !database.equals(System.getenv("FLEET_PG_TEST_USERNAME"))) {
            throw new IllegalStateException("dedicated same-role PostgreSQL test database required");
        }
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "url", base + "?connectTimeout=3&socketTimeout=30&tcpKeepAlive=true");
        ReflectionTestUtils.setField(config, "username", System.getenv("FLEET_PG_TEST_USERNAME"));
        ReflectionTestUtils.setField(config, "password", System.getenv("FLEET_PG_TEST_PASSWORD"));
        ReflectionTestUtils.setField(config, "driverClassName", "org.postgresql.Driver");
        try (DruidDataSource pool = config.master()) {
            pool.setDefaultReadOnly(true);
            pool.init();
            try (Connection connection = pool.getConnection()) {
                assertThat(pool.getConnectTimeout()).isEqualTo(3000);
                assertThat(pool.getSocketTimeout()).isEqualTo(30000);
                assertThat(connection.getNetworkTimeout()).isEqualTo(30000);
                assertThat(connection.unwrap(org.postgresql.jdbc.PgConnection.class).getNetworkTimeout()).isEqualTo(30000);
                assertThat(connection.isReadOnly()).isTrue();
                try (var query = connection.createStatement()) {
                    query.setQueryTimeout(3);
                    try (var rows = query.executeQuery("SELECT 1 FROM pg_sleep(0.12)")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(1);
                    }
                }
                try (var tables = connection.getMetaData().getTables(null, "public", "fleet_timeout_absent", new String[] {"TABLE"})) {
                    assertThat(tables.next()).isFalse();
                }
            }
        } catch (SQLException failure) {
            throw new AssertionError("isolated read-only PostgreSQL timeout contract failed; error class="
                    + failure.getClass().getName());
        }
    }
}
