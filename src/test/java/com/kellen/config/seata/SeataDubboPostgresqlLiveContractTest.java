package com.kellen.config.seata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式 opt-in 的基础设施联调：真实 TC、两个独立 JVM、Triple 和两个独立 PG 测试库。
 * 普通 test 不联网；不把探针成功扩大为业务服务已有全局事务或 Nacos 配置已启用。
 */
@EnabledIfEnvironmentVariable(named = "FLEET_SEATA_LIVE", matches = "true")
@Timeout(360)
class SeataDubboPostgresqlLiveContractTest {

    @Test
    void actualTcCommitsAndCompensatesTwoLocallyCommittedBranchesAcrossJvms() throws Exception {
        String table = "seata_probe_" + UUID.randomUUID().toString().replace("-", "");
        String tenant = "probe-" + UUID.randomUUID();
        Path directory = Files.createTempDirectory("fleet-seata-live-");
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Set<String> xids = new LinkedHashSet<>();
        Process provider = null;
        Process caller = null;
        boolean success = false;
        Set<Connection> fixtures = new LinkedHashSet<>();
        try (Connection first = connection("A"); Connection second = connection("B")) {
            assertThat(first.getCatalog().equals(second.getCatalog()))
                    .as("two distinct dedicated PostgreSQL databases required").isFalse();
            requireUndoTable(first);
            requireUndoTable(second);
            try {
                createFixture(first, table, tenant);
                fixtures.add(first);
                createFixture(second, table, tenant);
                fixtures.add(second);
                writeSeataConfiguration(directory);
                int port;
                try (ServerSocket reservation = new ServerSocket(0, 1, java.net.InetAddress.getByName("::1"))) {
                    port = reservation.getLocalPort();
                }
                provider = launch("provider", "B", table, tenant, port, directory);
                Properties providerReady = evidence(directory, "provider-ready", provider);
                caller = launch("caller", "A", table, tenant, port, directory);
                Properties callerReady = evidence(directory, "caller-ready", caller);
                assertThat(caller.pid()).isNotEqualTo(provider.pid()).isNotEqualTo(ProcessHandle.current().pid());
                assertThat(callerReady.getProperty("pid")).isEqualTo(Long.toString(caller.pid()));
                assertThat(providerReady.getProperty("pid")).isEqualTo(Long.toString(provider.pid()));

                for (String caseId : List.of("commit", "rollback")) {
                    caller.getOutputStream().write((caseId + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    caller.getOutputStream().flush();
                    Properties begin = evidence(directory, "caller-begin-" + caseId, caller);
                    String xid = begin.getProperty("xid");
                    assertThat(xid != null && !xid.isBlank()).as("TC assigned a real XID").isTrue();
                    xids.add(xid);
                    Properties outcome = evidence(directory, "caller-" + caseId, caller);
                    assertThat(outcome.getProperty("outcome"))
                            .as("global probe outcome; failure details remain in private node log")
                            .isEqualTo(caseId.equals("commit") ? "committed" : "rolled-back");
                    Properties remote = evidence(directory, "provider-" + caseId, provider);
                    assertThat(xid.equals(remote.getProperty("xid")))
                            .as("same TC XID propagated by the utils Dubbo filter").isTrue();
                    assertThat(remote.getProperty("tenant")).isEqualTo(tenant);
                    assertThat(remote.getProperty("pid")).isEqualTo(Long.toString(provider.pid()));
                    assertThat(outcome.getProperty("amountBeforeRemote")).isEqualTo("110");
                    assertThat(remote.getProperty("amount")).isEqualTo("120");
                    assertThat(outcome.getProperty("undoBeforeRemote")).isEqualTo("1");
                    assertThat(remote.getProperty("undo")).isEqualTo("1");
                    assertThat(outcome.getProperty("contextCleared")).isEqualTo("true");
                    assertThat(outcome.getProperty("tcStatus")).isEqualTo("Finished");
                    if (caseId.equals("commit")) {
                        assertThat(outcome.getProperty("outcome")).isEqualTo("committed");
                        assertThat(outcome.getProperty("status")).isEqualTo("Committed");
                        assertThat(xid.equals(outcome.getProperty("remoteXid"))).isTrue();
                        awaitState(first, second, table, caseId, xid, 110, 120);
                    } else {
                        // Provider 已从独立原生连接看到两阶段第一阶段提交后才抛错。
                        assertThat(outcome.getProperty("outcome")).isEqualTo("rolled-back");
                        assertThat(outcome.getProperty("status")).isEqualTo("Rollbacked");
                        awaitState(first, second, table, caseId, xid, 100, 100);
                    }
                }
            } finally {
                // 若 TC 补偿仍未完成，保留隔离表与私有证据，不能删掉待补偿记录掩盖失败。
                stop(caller);
                stop(provider);
                boolean transactionsFinished = true;
                for (String caseId : List.of("commit", "rollback")) {
                    Path begin = directory.resolve("caller-begin-" + caseId + ".properties");
                    if (Files.exists(begin)) {
                        xids.add(read(begin).getProperty("xid"));
                        Path outcome = directory.resolve("caller-" + caseId + ".properties");
                        transactionsFinished &= Files.exists(outcome) && "Finished".equals(read(outcome).getProperty("tcStatus"));
                    }
                }
                if (transactionsFinished && allUndoCleared(first, second, xids)) {
                    for (Connection fixture : fixtures) dropFixture(fixture, table);
                } else {
                    throw new IllegalStateException("probe undo remains; isolated fixtures and private evidence retained");
                }
            }
            success = true;
        } finally {
            if (success) {
                try (var files = Files.walk(directory)) {
                    for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    private Connection connection(String role) throws Exception {
        return SeataProbeDatabase.connect(SeataProbeDatabase.required("FLEET_SEATA_PG_" + role + "_URL"),
                SeataProbeDatabase.required("FLEET_SEATA_PG_" + role + "_USERNAME"),
                SeataProbeDatabase.required("FLEET_SEATA_PG_" + role + "_PASSWORD"));
    }

    private void requireUndoTable(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT to_regclass('public.undo_log') IS NOT NULL")) {
            rows.next();
            assertThat(rows.getBoolean(1)).as("common-infra-schema.sql must run before RM startup").isTrue();
        }
    }

    private void createFixture(Connection connection, String table, String tenant) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE public." + SeataProbeDatabase.table(table)
                    + " (id varchar(32) PRIMARY KEY, tenant_id varchar(64) NOT NULL, amount integer NOT NULL)");
        }
        try (var insert = connection.prepareStatement("INSERT INTO public." + table + " VALUES (?, ?, 100)")) {
            for (String caseId : List.of("commit", "rollback")) {
                insert.setString(1, caseId);
                insert.setString(2, tenant);
                insert.executeUpdate();
            }
        } catch (Exception failure) {
            dropFixture(connection, table);
            throw failure;
        }
    }

    private void dropFixture(Connection connection, String table) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE public." + SeataProbeDatabase.table(table));
        }
    }

    private void writeSeataConfiguration(Path directory) throws Exception {
        String address = SeataProbeDatabase.required("FLEET_SEATA_TC_ADDRESS");
        if (!address.matches("[a-zA-Z0-9._-]+:[0-9]{1,5}")) {
            throw new IllegalArgumentException("single TC host:port required");
        }
        Properties registry = new Properties();
        registry.setProperty("registry.type", "file");
        registry.setProperty("config.type", "file");
        registry.setProperty("config.file.name", directory.resolve("file.properties").toString());
        store(directory.resolve("registry.properties"), registry);
        Properties config = new Properties();
        config.setProperty("service.vgroupMapping.fleet_probe_tx", "default");
        config.setProperty("service.default.grouplist", address);
        config.setProperty("service.disableGlobalTransaction", "false");
        config.setProperty("client.undo.logSerialization", "jackson");
        config.setProperty("client.rm.reportSuccessEnable", "true");
        store(directory.resolve("file.properties"), config);
    }

    private void store(Path path, Properties properties) throws Exception {
        try (var output = Files.newOutputStream(path)) { properties.store(output, "isolated live probe only"); }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    private Process launch(String role, String database, String table, String tenant, int port, Path directory) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m", "-Xmx256m", "-XX:ActiveProcessorCount=2",
                "-Ddubbo.protocol.triple.rest.enabled=false",
                "-Dseata.config.name=" + directory.resolve("registry.properties"),
                "-cp", runtimeClasspath(), SeataProbeNode.class.getName(), role);
        var environment = builder.environment();
        environment.put("FLEET_PROBE_TABLE", table);
        environment.put("FLEET_PROBE_TENANT", tenant);
        environment.put("FLEET_PROBE_PORT", Integer.toString(port));
        environment.put("FLEET_PROBE_DIRECTORY", directory.toString());
        // Dubbo 拒绝显式 127.* bind；IPv6 loopback 可通过配置校验且仍严格仅限本机。
        environment.put("DUBBO_IP_TO_BIND", "::1");
        environment.put("DUBBO_IP_TO_REGISTRY", "::1");
        environment.put("FLEET_PROBE_JDBC_URL", SeataProbeDatabase.required("FLEET_SEATA_PG_" + database + "_URL"));
        environment.put("FLEET_PROBE_JDBC_USERNAME", SeataProbeDatabase.required("FLEET_SEATA_PG_" + database + "_USERNAME"));
        environment.put("FLEET_PROBE_JDBC_PASSWORD", SeataProbeDatabase.required("FLEET_SEATA_PG_" + database + "_PASSWORD"));
        // 子 JVM 不继承另一测试库的登录资料，也不需要 Gradle/Maven 或 Nacos 凭据。
        environment.keySet().removeIf(key -> key.startsWith("FLEET_SEATA_PG_") || key.startsWith("GITHUB_")
                || key.startsWith("PRIVATE_MAVEN_"));
        Path log = directory.resolve(role + ".log");
        Files.createFile(log, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        return builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private String runtimeClasspath() throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
            }
        }
        if (paths.isEmpty()) paths.addAll(List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        return String.join(File.pathSeparator, paths);
    }

    private Properties evidence(Path directory, String name, Process process) throws Exception {
        Path path = directory.resolve(name + ".properties");
        boolean transactionOutcome = name.equals("caller-commit") || name.equals("caller-rollback");
        long deadline = System.nanoTime() + Duration.ofSeconds(transactionOutcome ? 150 : 45).toNanos();
        while (!Files.exists(path) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
        assertThat(Files.exists(path)).as("%s evidence missing; inspect private node log", name).isTrue();
        return read(path);
    }

    private Properties read(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private void awaitState(Connection first, Connection second, String table, String caseId, String xid,
                            int firstValue, int secondValue) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        do {
            if (SeataProbeDatabase.value(first, table, caseId) == firstValue
                    && SeataProbeDatabase.value(second, table, caseId) == secondValue
                    && allUndoCleared(first, second, Set.of(xid))) return;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("two-database values or AT undo cleanup did not reach expected state");
    }

    private boolean allUndoCleared(Connection first, Connection second, Set<String> xids) throws Exception {
        for (String xid : xids) {
            if (SeataProbeDatabase.undoCount(first, xid) != 0 || SeataProbeDatabase.undoCount(second, xid) != 0) return false;
        }
        return true;
    }

    private void stop(Process process) throws Exception {
        if (process == null) return;
        try { process.getOutputStream().close(); } catch (java.io.IOException ignored) { /* 节点可能已失败退出。 */ }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy();
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(3, TimeUnit.SECONDS);
        }
    }
}
