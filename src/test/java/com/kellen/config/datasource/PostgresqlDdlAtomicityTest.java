package com.kellen.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.DdlAutoConfiguration;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.Banner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * 真实 PostgreSQL 上区分脚本提交、历史提交和启动失败；不以 H2 或 setter 断言代替事务。
 *
 * <p>默认执行生产适配；显式 FLEET_DDL_OFFICIAL_BASELINE=1 可重跑官方实现的两项红基线（3.5.16 发现，3.5.18.1 仍复现）。</p>
 */
@EnabledIfEnvironmentVariable(named = "FLEET_PG_TEST_URL", matches = "jdbc:postgresql:.*")
@ExtendWith(OutputCaptureExtension.class)
class PostgresqlDdlAtomicityTest {

    private static final String SUCCESS = "db/postgresql/fleet-ddl-commit-regression.sql";
    private static final String FAILURE = "db/postgresql/fleet-ddl-error-regression.sql";
    private DriverManagerDataSource dataSource;

    /** 所有写入之前守卫专用 NAS 测试库，仅准备本测试自己的对象。 */
    @BeforeEach
    void prepareIsolatedObjects() throws Exception {
        dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv("FLEET_PG_TEST_URL"));
        dataSource.setUsername(System.getenv("FLEET_PG_TEST_USERNAME"));
        dataSource.setPassword(System.getenv("FLEET_PG_TEST_PASSWORD"));
        Properties properties = new Properties();
        properties.setProperty("connectTimeout", "3");
        properties.setProperty("socketTimeout", "5");
        dataSource.setConnectionProperties(properties);
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getCatalog().startsWith("fleet_pg_test_")) {
                throw new IllegalStateException("DEDICATED_PG_TEST_DATABASE_REQUIRED");
            }
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS public.ddl_history (script varchar(500) PRIMARY KEY, type varchar(30) NOT NULL, version varchar(30) NOT NULL)");
            }
        }
        cleanOnlyOwnObjects();
    }

    /** 不清空公共历史表，不删除其他智能体的随机 schema 或对象。 */
    @AfterEach
    void cleanOnlyOwnObjects() throws Exception {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getCatalog().startsWith("fleet_pg_test_")) {
                throw new IllegalStateException("DEDICATED_PG_TEST_DATABASE_REQUIRED");
            }
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS public.fleet_ddl_commit_regression");
                statement.execute("DROP TABLE IF EXISTS public.fleet_ddl_error_regression");
            }
            try (var statement = connection.prepareStatement("DELETE FROM public.ddl_history WHERE script IN (?, ?)")) {
                statement.setString(1, SUCCESS);
                statement.setString(2, FAILURE);
                statement.executeUpdate();
            }
        }
    }

    /** 新连接读回证明最后的 history 已提交，第二次启动不能重放已经成功的文件。 */
    @Test
    void scriptsAndLastHistoryMustRemainCommittedAcrossASecondStartup() throws Exception {
        runner(SUCCESS, false).run(new DefaultApplicationArguments());
        long firstHistory = historyCount(SUCCESS);
        long firstExecutions = number("SELECT executions FROM public.fleet_ddl_commit_regression WHERE id = 1");
        runner(SUCCESS, false).run(new DefaultApplicationArguments());
        long secondHistory = historyCount(SUCCESS);
        long secondExecutions = number("SELECT executions FROM public.fleet_ddl_commit_regression WHERE id = 1");
        assertSoftly(softly -> {
            softly.assertThat(firstExecutions).as("first script is durable").isEqualTo(1);
            softly.assertThat(firstHistory).as("last history is durable on another connection").isEqualTo(1);
            softly.assertThat(secondExecutions).as("second startup must not replay the file").isEqualTo(1);
            softly.assertThat(secondHistory).as("one durable history row").isEqualTo(1);
        });
    }

    /** 实际 SpringApplication 调用 runner，不能吞掉 SQL 错误后进入 ready。 */
    @Test
    void halfScriptFailureMustRollbackAndAbortSpringBootStartup() throws Exception {
        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setDefaultProperties(Map.of(
                "spring.config.name", "pg-ddl-atomicity-fixture",
                "spring.cloud.bootstrap.enabled", "false",
                "spring.cloud.nacos.config.enabled", "false",
                "spring.cloud.nacos.discovery.enabled", "false",
                "seata.enabled", "false"));
        application.addInitializers(context -> {
            if ("1".equals(System.getenv("FLEET_DDL_OFFICIAL_BASELINE"))) {
                context.getBeanFactory().registerSingleton("ddlRunner", runner(FAILURE, true));
            } else {
                context.getBeanFactory().registerSingleton("businessDdl", definition(dataSource, List.of(FAILURE)));
            }
        });
        boolean rejected = false;
        try (var ignored = application.run()) {
            // 成功返回意味着应用已经经过 ApplicationRunner 阶段；此路径本应失败。
        } catch (RuntimeException expected) {
            rejected = true;
        }
        boolean startupRejected = rejected;
        long remainingTables = number("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'fleet_ddl_error_regression'");
        long history = historyCount(FAILURE);
        assertSoftly(softly -> {
            softly.assertThat(startupRejected).as("Spring Boot startup fails closed").isTrue();
            softly.assertThat(remainingTables).as("half-executed table is rolled back").isZero();
            softly.assertThat(history).as("failed file never receives history").isZero();
        });
    }

    /** 前一个文件及其 history 也必须随末文件失败回滚，不能只保护单文件。 */
    @Test
    void laterScriptFailureRollsBackEarlierScriptAndItsHistory() throws Exception {
        assertThatThrownBy(() -> productionRunner(dataSource, List.of(SUCCESS, FAILURE)).run(new DefaultApplicationArguments()))
                .hasMessage("PG_DDL_SCRIPT_FAILED").hasNoCause();
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('fleet_ddl_commit_regression','fleet_ddl_error_regression')")).isZero();
        assertThat(historyCount(SUCCESS)).isZero();
        assertThat(historyCount(FAILURE)).isZero();
    }

    /** 使用真实 helper，定点拒绝最后的 history INSERT；已成功的 SQL 仍必须回滚且不输出原文。 */
    @Test
    void failedHistoryInsertRollsBackCompletedScriptWithoutRawSqlOrCause(CapturedOutput output) throws Exception {
        DataSource rejectHistory = mappedSource(connection -> proxyConnection(connection, (method, arguments) -> {
            if (method.equals("prepareStatement") && ((String) arguments[0]).toLowerCase().contains("insert into")
                    && ((String) arguments[0]).contains("ddl_history")) {
                throw new SQLException("SYNTHETIC_PRIVATE_DATABASE_DETAIL");
            }
            return null;
        }));
        assertThatThrownBy(() -> productionRunner(rejectHistory, List.of(SUCCESS)).run(new DefaultApplicationArguments()))
                .hasMessage("PG_DDL_SCRIPT_FAILED").hasNoCause();
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='fleet_ddl_commit_regression'")).isZero();
        assertThat(historyCount(SUCCESS)).isZero();
        assertThat(output.getAll()).doesNotContain("SYNTHETIC_PRIVATE_DATABASE_DETAIL", "UPDATE public.fleet_ddl_commit_regression", "CREATE TABLE IF NOT EXISTS public.fleet_ddl_commit_regression");
    }

    /** 两个启动都真实等待锁；READ COMMITTED 保证后继启动重新读取已提交 history。 */
    @Test
    void simultaneousStartupsWaitForDatabaseLockThenExecuteExactlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, PostgresqlDdlApplicationRunner.ADVISORY_LOCK_KEY);
                lock.execute();
            }
            DataSource repeatableRead = mappedSource(connection -> {
                try { connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); }
                catch (SQLException error) { throw new IllegalStateException("FIXTURE_CONNECTION_FAILED"); }
                return connection;
            });
            var first = executor.submit(() -> productionRunner(repeatableRead, List.of(SUCCESS)).run(new DefaultApplicationArguments()));
            var second = executor.submit(() -> productionRunner(repeatableRead, List.of(SUCCESS)).run(new DefaultApplicationArguments()));
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            long waiting;
            do {
                waiting = number("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted AND classid="
                        + (PostgresqlDdlApplicationRunner.ADVISORY_LOCK_KEY >>> 32) + " AND objid="
                        + (PostgresqlDdlApplicationRunner.ADVISORY_LOCK_KEY & 0xffffffffL));
                if (waiting < 2) { Thread.sleep(30); }
            } while (waiting < 2 && System.nanoTime() < deadline);
            assertThat(waiting).as("both runners really waited on the transaction lock").isEqualTo(2);
            blocker.commit();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(number("SELECT executions FROM public.fleet_ddl_commit_regression WHERE id=1")).isEqualTo(1);
        assertThat(historyCount(SUCCESS)).isEqualTo(1);
    }

    /** 即使借出连接原先指向租户 schema，也只在 public 执行；归还前恢复连接状态。 */
    @Test
    void publicScopeAndPooledConnectionStateAreRestored() throws Exception {
        String schema = "fleet_ddl_scope_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        AtomicBoolean returnedWithAutoCommit = new AtomicBoolean();
        AtomicBoolean returnedWithSearchPath = new AtomicBoolean();
        AtomicInteger returnedIsolation = new AtomicInteger();
        try {
            DataSource scoped = mappedSource(connection -> {
                try {
                    connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    try (var statement = connection.createStatement()) { statement.execute("SET search_path TO " + schema); }
                } catch (SQLException error) { throw new IllegalStateException("FIXTURE_CONNECTION_FAILED"); }
                return proxyConnection(connection, (method, arguments) -> {
                    if (method.equals("close")) {
                        returnedWithAutoCommit.set(connection.getAutoCommit());
                        returnedIsolation.set(connection.getTransactionIsolation());
                        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW search_path")) {
                            rows.next(); returnedWithSearchPath.set(schema.equals(rows.getString(1)));
                        }
                    }
                    return null;
                });
            });
            productionRunner(scoped, List.of("db/postgresql/fleet-ddl-unqualified-regression.sql")).run(new DefaultApplicationArguments());
            assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='fleet_ddl_commit_regression'")).isEqualTo(1);
            assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema + "'")).isZero();
            assertThat(returnedWithAutoCommit).isTrue();
            assertThat(returnedWithSearchPath).isTrue();
            assertThat(returnedIsolation).hasValue(Connection.TRANSACTION_REPEATABLE_READ);
        } finally {
            try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema);
                statement.execute("DELETE FROM public.ddl_history WHERE script='db/postgresql/fleet-ddl-unqualified-regression.sql'");
            }
        }
    }

    /** 官方基线，只屏蔽输出原文；不修饰其真实 commit、rollback 或异常传播行为。 */
    private DdlApplicationRunner runner(String path, boolean autoCommit) {
        IDdl ddl = new IDdl() {
            @Override public void runScript(Consumer<DataSource> consumer) { consumer.accept(dataSource); }
            @Override public List<String> getSqlFiles() { return List.of(path); }
        };
        if (!"1".equals(System.getenv("FLEET_DDL_OFFICIAL_BASELINE"))) {
            return productionRunner(dataSource, List.of(path));
        }
        DdlApplicationRunner runner = new DdlApplicationRunner(List.of(ddl));
        runner.setAutoCommit(autoCommit);
        runner.setScriptRunnerConsumer(script -> {
            script.setLogWriter(null);
            script.setErrorLogWriter(null);
            script.setStopOnError(true);
        });
        runner.setDdlScriptErrorHandler((script, error) -> {
            throw new IllegalStateException("PG_DDL_FIXTURE_REJECTED");
        });
        return runner;
    }

    /** 从真实 Boot 自动配置 bean 获取生产 runner，官方自动配置必须同时存在并退让。 */
    private DdlApplicationRunner productionRunner(DataSource source, List<String> scripts) {
        IDdl ddl = definition(source, scripts);
        AtomicReference<DdlApplicationRunner> result = new AtomicReference<>();
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DdlAutoConfiguration.class, PostgresqlDdlAutoConfiguration.class))
                .withBean(IDdl.class, () -> ddl).run(application -> {
                    assertThat(application).hasSingleBean(DdlApplicationRunner.class);
                    assertThat(application.getBean(DdlApplicationRunner.class)).isInstanceOf(PostgresqlDdlApplicationRunner.class);
                    result.set(application.getBean(DdlApplicationRunner.class));
                });
        return result.get();
    }

    private IDdl definition(DataSource source, List<String> scripts) {
        return new IDdl() {
            @Override public void runScript(Consumer<DataSource> consumer) { consumer.accept(source); }
            @Override public List<String> getSqlFiles() { return scripts; }
        };
    }

    private DataSource mappedSource(Function<Connection, Connection> mapper) {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return mapper.apply(dataSource.getConnection()); }
            @Override public Connection getConnection(String username, String password) throws SQLException { return getConnection(); }
        };
    }

    @FunctionalInterface
    private interface ConnectionProbe { Object invoke(String method, Object[] arguments) throws SQLException; }

    private Connection proxyConnection(Connection physical, ConnectionProbe probe) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
            probe.invoke(method.getName(), arguments);
            try { return method.invoke(physical, arguments); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }

    /** 只读取本测试精确脚本标识的历史行数。 */
    private long historyCount(String path) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM public.ddl_history WHERE script = ?")) {
            statement.setString(1, path);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    /** 仅执行本文件的固定合成对象计数查询。 */
    private long number(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DdlAutoConfiguration.class, PostgresqlDdlAutoConfiguration.class})
    static class EmptyConfiguration {
    }
}
