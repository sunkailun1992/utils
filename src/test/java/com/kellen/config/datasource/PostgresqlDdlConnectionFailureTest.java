package com.kellen.config.datasource;

import com.baomidou.mybatisplus.extension.ddl.IDdl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 无需破坏共享 NAS 公共表，定点验证缺失基础表和连接故障的失败关闭路径。 */
class PostgresqlDdlConnectionFailureTest {
    private final DataSource source = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final ResultSet commonExists = mock(ResultSet.class);

    @BeforeEach
    void prepareConnection() throws Exception {
        when(source.getConnection()).thenReturn(connection);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(commonExists);
        when(commonExists.next()).thenReturn(true);
        when(commonExists.getBoolean(1)).thenReturn(false);
    }

    @Test
    void missingManualCommonSchemaIsRejectedWithoutCreatingHistoryOrRunningScript() throws Exception {
        assertThatThrownBy(() -> runner().run(new DefaultApplicationArguments()))
                .hasMessage("PG_DDL_COMMON_SCHEMA_REQUIRED").hasNoCause();
        verify(connection, never()).commit();
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        verify(connection).close();
        verify(statement).execute("SET LOCAL search_path TO public");
    }

    @Test
    void uncertainRollbackNeverEnablesAutoCommitAndCannotLeakDriverDetails() throws Exception {
        doThrow(new SQLException("SYNTHETIC_PRIVATE_ROLLBACK_DETAIL")).when(connection).rollback();
        assertThatThrownBy(() -> runner().run(new DefaultApplicationArguments()))
                .hasMessage("PG_DDL_ROLLBACK_FAILED").hasNoCause();
        verify(connection, never()).commit();
        verify(connection, never()).setAutoCommit(true);
        verify(connection).close();
    }

    @Test
    void helperCannotAdoptAnAlreadyActiveTransaction() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        assertThatThrownBy(() -> runner().run(new DefaultApplicationArguments()))
                .hasMessage("PG_DDL_DEDICATED_CONNECTION_REQUIRED").hasNoCause();
        verify(connection, never()).commit();
        verify(connection, never()).setAutoCommit(true);
        verify(connection).close();
    }

    private PostgresqlDdlApplicationRunner runner() {
        return new PostgresqlDdlApplicationRunner(List.of(new IDdl() {
            @Override public void runScript(Consumer<DataSource> consumer) { consumer.accept(source); }
            @Override public List<String> getSqlFiles() { return List.of("db/postgresql/common-infra-schema.sql"); }
        }));
    }
}
