package com.kellen.config.seata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** 专用测试库与合成表的写入边界；任何业务库或调用方提供的 SQL 标识符均拒绝。 */
final class SeataProbeDatabase {

    private SeataProbeDatabase() { }

    /** 在返回连接前核对实际数据库名，不能只信 URL 中的名称。 */
    static Connection connect(String url, String username, String password) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "3");
        properties.setProperty("socketTimeout", "10");
        Connection connection;
        try {
            connection = DriverManager.getConnection(url, properties);
        } catch (SQLException failure) {
            throw new SQLException("probe PostgreSQL connection failed; connection details suppressed",
                    failure.getSQLState(), failure.getErrorCode());
        }
        if (!connection.getCatalog().startsWith("fleet_pg_test_")) {
            connection.close();
            throw new IllegalStateException("dedicated fleet_pg_test_ database required");
        }
        return connection;
    }

    /** 表名只能由测试控制器生成；不接受 RPC 参数决定表名。 */
    static String table(String value) {
        if (value == null || !value.matches("seata_probe_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("invalid synthetic probe table");
        }
        return value;
    }

    static int value(Connection connection, String table, String caseId) throws SQLException {
        try (var query = connection.prepareStatement("SELECT amount FROM public." + table(table) + " WHERE id = ?")) {
            query.setString(1, caseId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("synthetic probe row missing");
                return rows.getInt(1);
            }
        }
    }

    static int undoCount(Connection connection, String xid) throws SQLException {
        try (var query = connection.prepareStatement("SELECT count(*) FROM public.undo_log WHERE xid = ?")) {
            query.setString(1, xid);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }

    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("required probe setting missing: " + key);
        return value;
    }
}
