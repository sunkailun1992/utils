package com.kellen.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.extension.ddl.DdlHelper;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import com.baomidou.mybatisplus.extension.ddl.history.PostgreDdlGenerator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL 启动 DDL：官方脚本与历史写入共用一个外层事务，失败阻止应用就绪。
 *
 * <p>原子边界是每次 IDdl 的数据源回调；不提供跨数据库事务。公共表必须事先手动初始化。</p>
 */
public final class PostgresqlDdlApplicationRunner extends DdlApplicationRunner implements Ordered {

    // 同一数据库 public DDL 共用事务锁；与业务 ID、租户或外部输入无关。
    static final long ADVISORY_LOCK_KEY = 0x466c65657444444cL;
    private final List<IDdl> ddlList;

    public PostgresqlDdlApplicationRunner(List<IDdl> ddlList) {
        super(ddlList);
        this.ddlList = List.copyOf(ddlList);
    }

    /** 业务初始化 runner 只能在 schema 与历史都持久化之后执行。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        for (IDdl ddl : ddlList) {
            try {
                if (ddl.getDdlGenerator() != null) {
                    throw rejected("PG_DDL_CUSTOM_GENERATOR_REJECTED");
                }
                List<String> scripts = List.copyOf(ddl.getSqlFiles());
                for (String script : scripts) {
                    PostgresqlDdlScriptPolicy.validate(script);
                }
                ddl.runScript(dataSource -> execute(dataSource, scripts));
            } catch (DdlRejectedException error) {
                throw error;
            } catch (Exception error) {
                // 原始 SQLException 可能含 SQL、连接参数或数据，不能进入启动日志的 cause 链。
                throw rejected("PG_DDL_FAILED");
            }
        }
    }

    private void execute(DataSource dataSource, List<String> scripts) {
        Connection connection = null;
        boolean transactionStarted = false;
        boolean committed = false;
        boolean stateCaptured = false;
        int previousIsolation = Connection.TRANSACTION_READ_COMMITTED;
        DdlRejectedException failure = null;
        try {
            connection = dataSource.getConnection();
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())
                    || !connection.getAutoCommit() || connection.isReadOnly()) {
                throw rejected("PG_DDL_DEDICATED_CONNECTION_REQUIRED");
            }
            previousIsolation = connection.getTransactionIsolation();
            stateCaptured = true;
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            transactionStarted = true;
            try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, ADVISORY_LOCK_KEY);
                lock.execute();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL search_path TO public");
                try (var rows = statement.executeQuery("SELECT to_regclass('public.ddl_history') IS NOT NULL AND to_regclass('public.undo_log') IS NOT NULL")) {
                    if (!rows.next() || !rows.getBoolean(1)) {
                        throw rejected("PG_DDL_COMMON_SCHEMA_REQUIRED");
                    }
                }
            }
            DdlHelper.runScript(PostgreDdlGenerator.newInstance(), helperConnection(connection), scripts, runner -> {
                runner.setLogWriter(null);
                runner.setErrorLogWriter(null);
                runner.setStopOnError(true);
            }, false, (script, error) -> { throw rejected("PG_DDL_SCRIPT_FAILED"); });
            // 包含最后一个脚本之后由 SqlRunner 写入的历史，只有这里能提交真实连接。
            connection.commit();
            committed = true;
        } catch (Exception error) {
            failure = error instanceof DdlRejectedException rejected ? rejected : rejected("PG_DDL_FAILED");
        } finally {
            if (connection != null) {
                boolean resetAllowed = true;
                if (transactionStarted && !committed) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {
                        // rollback 未被确认时绝不能用 setAutoCommit(true) 意外提交残留事务。
                        resetAllowed = false;
                        failure = rejected("PG_DDL_ROLLBACK_FAILED");
                    }
                }
                try {
                    if (transactionStarted && resetAllowed) {
                        connection.setAutoCommit(true);
                    }
                    if (stateCaptured && resetAllowed) {
                        connection.setTransactionIsolation(previousIsolation);
                    }
                } catch (SQLException ignored) {
                    failure = rejected("PG_DDL_CONNECTION_RESET_FAILED");
                }
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    failure = rejected("PG_DDL_CONNECTION_CLOSE_FAILED");
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** 仅交给固定版本官方 helper；拦截其内部事务边界，不暴露为公共连接工具。 */
    private static Connection helperConnection(Connection physical) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    String name = method.getName();
                    if (("commit".equals(name) || "rollback".equals(name) || "close".equals(name))
                            && (arguments == null || arguments.length == 0)) {
                        return null;
                    }
                    if ("getAutoCommit".equals(name)) {
                        return false;
                    }
                    if ("setAutoCommit".equals(name) && Boolean.FALSE.equals(arguments[0])) {
                        return null;
                    }
                    if ("unwrap".equals(name) && arguments[0] == Connection.class) {
                        return proxy;
                    }
                    if ("isWrapperFor".equals(name)) {
                        return arguments[0] == Connection.class;
                    }
                    if (List.of("setAutoCommit", "setTransactionIsolation", "setSchema", "setCatalog", "setReadOnly",
                            "unwrap", "abort", "setSavepoint", "releaseSavepoint", "rollback").contains(name)) {
                        throw new SQLException("PG_DDL_HELPER_TRANSACTION_CONTROL_REJECTED");
                    }
                    if ("toString".equals(name)) {
                        return "PostgresqlDdlTransactionConnection";
                    }
                    try {
                        return method.invoke(physical, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    private static DdlRejectedException rejected(String code) {
        return new DdlRejectedException(code);
    }

    /** 只保留固定代码；不传播原始 SQL、数据库异常消息、路径或凭据。 */
    private static final class DdlRejectedException extends IllegalStateException {
        private DdlRejectedException(String code) {
            super(code);
        }
    }
}
