package com.kellen.config.seata;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 默认离线验证动态 SQL 表名的拒绝边界，避免 opt-in 工具变成任意表写入口。 */
class SeataProbeSafetyTest {

    @Test
    void rejectsBusinessTablesAndSqlFragments() {
        for (String value : new String[] {"auth_user", "undo_log", "seata_probe_x; DROP TABLE undo_log", "public.seata_probe_01"}) {
            assertThatThrownBy(() -> SeataProbeDatabase.table(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void closesAndRejectsConnectionWhoseActualCatalogIsBusinessDatabase() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("github_dev_ai");
        Driver driver = mock(Driver.class);
        String url = "jdbc:seata-probe-test:guard";
        when(driver.connect(eq(url), any(Properties.class))).thenReturn(connection);
        DriverManager.registerDriver(driver);
        try {
            assertThatThrownBy(() -> SeataProbeDatabase.connect(url, "synthetic", "synthetic"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("dedicated fleet_pg_test_ database required");
            verify(connection).close();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }
}
