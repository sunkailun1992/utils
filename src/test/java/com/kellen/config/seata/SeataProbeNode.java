package com.kellen.config.seata;

import com.kellen.config.datasource.DynamicDataSourceConfig;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.RMClient;
import org.apache.seata.tm.TMClient;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 独立 JVM 中的真实 TM/RM；不扫描业务 Bean，不读取 Nacos，不启动额外基础设施。
 * 两端仅用 loopback Triple，现有 utils filter 独立承担 XID 与合成租户的透传。
 */
public final class SeataProbeNode {

    /** 禁用第三方同类 filter，防止它代替受验收的 utils filter 传播 XID。 */
    private static final String FILTERS = "-seata-provider,-seata-consumer,kellenContextPropagation";

    private final String table = SeataProbeDatabase.table(SeataProbeDatabase.required("FLEET_PROBE_TABLE"));
    private final String tenant = SeataProbeDatabase.required("FLEET_PROBE_TENANT");
    private final Path directory = Path.of(SeataProbeDatabase.required("FLEET_PROBE_DIRECTORY"));
    private final String url = SeataProbeDatabase.required("FLEET_PROBE_JDBC_URL");
    private final String username = SeataProbeDatabase.required("FLEET_PROBE_JDBC_USERNAME");
    private final String password = SeataProbeDatabase.required("FLEET_PROBE_JDBC_PASSWORD");
    private TransactionTemplate localTransaction;
    private JdbcTemplate jdbc;

    /** 由受 opt-in 保护的父测试启动；命令行不携带凭据或数据库地址。 */
    public static void main(String[] args) {
        try {
            if (!"true".equals(System.getenv("FLEET_SEATA_LIVE"))) {
                throw new IllegalStateException("explicit live probe opt-in required");
            }
            new SeataProbeNode().run(args[0]);
        } catch (Throwable failure) {
            // 第三方驱动异常可能包含连接串；受控状态只保留类型，原始日志留在私有临时目录。
            System.err.println("probe node failed: " + failure.getClass().getSimpleName());
            failure.printStackTrace(System.err);
            System.exit(2);
        }
        System.exit(0);
    }

    private void run(String role) throws Exception {
        try (Connection ignored = rawConnection()) {
            // 在初始化连接池、RM 和任何写操作前先检查真实库名。
        }
        // 执行 starter 正常的环境初始化，避免最小上下文缺少 Boot 环境和默认属性类型。
        org.apache.seata.spring.boot.autoconfigure.SeataCoreEnvironmentPostProcessor.init();
        new org.apache.seata.spring.boot.autoconfigure.SeataClientEnvironmentPostProcessor()
                .postProcessEnvironment(new StandardEnvironment(), null);
        // starter SPI 优先读取原生 system keys；没有 Boot 属性 Bean 时显式装入探针私有 file 配置。
        Properties registry = loadProperties(Path.of(System.getProperty("seata.config.name")));
        Properties service = loadProperties(Path.of(registry.getProperty("config.file.name")));
        registry.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
        service.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
        RMClient.init("fleet-seata-probe-" + role, "fleet_probe_tx");
        if (role.equals("caller")) TMClient.init("fleet-seata-probe-caller", "fleet_probe_tx");
        try (var context = dataSourceContext()) {
            DataSource dataSource = context.getBean("dataSource", DataSource.class);
            jdbc = new JdbcTemplate(dataSource);
            localTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            ApplicationConfig application = new ApplicationConfig("fleet-seata-probe-" + role);
            application.setQosEnable(false);
            application.setMetadataType("local");
            if (role.equals("provider")) runProvider(application); else runCaller(application);
        }
    }

    private AnnotationConfigApplicationContext dataSourceContext() {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (String prefix : new String[] {"postgresql", "postgresql-gray"}) {
            settings.put(prefix + ".url", url);
            settings.put(prefix + ".username", username);
            settings.put(prefix + ".password", password);
            settings.put(prefix + ".driverClassName", "org.postgresql.Driver");
        }
        settings.put("utils.datasource.pool.master.max-active", "2");
        settings.put("utils.datasource.pool.gray.max-active", "1");
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-seata-probe", settings));
        context.register(DynamicDataSourceConfig.class);
        context.refresh();
        return context;
    }

    private void runProvider(ApplicationConfig application) throws Exception {
        ServiceConfig<SeataProbeRpcService> service = new ServiceConfig<>();
        service.setApplication(application);
        service.setRegistry(new RegistryConfig("N/A"));
        ProtocolConfig protocol = new ProtocolConfig("tri", Integer.parseInt(SeataProbeDatabase.required("FLEET_PROBE_PORT")));
        protocol.setHost("::1");
        service.setProtocol(protocol);
        service.setInterface(SeataProbeRpcService.class);
        service.setFilter(FILTERS);
        service.setRef(this::providerWrite);
        service.export();
        try {
            writeEvidence("provider-ready", properties("pid", ProcessHandle.current().pid()));
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                while (input.readLine() != null) { /* 父进程关闭 stdin 后释放 provider。 */ }
            }
        } finally {
            service.unexport();
        }
    }

    private String[] providerWrite(String caseId, boolean failAfterCommit) {
        String xid = RootContext.getXID();
        if (xid == null || !tenant.equals(TenantContextHolder.getTenantId())) {
            throw new IllegalStateException("probe transaction context not propagated");
        }
        try {
            writeLocally(caseId, 20);
            try (Connection connection = rawConnection()) {
                // 独立原生连接必须能看见 after-image 和 undo；这排除仅本地未提交事务假象。
                writeEvidence("provider-" + caseId, properties("xid", xid, "pid", ProcessHandle.current().pid(),
                        "amount", SeataProbeDatabase.value(connection, table, caseId),
                        "undo", SeataProbeDatabase.undoCount(connection, xid), "tenant", TenantContextHolder.getTenantId()));
            }
            if (failAfterCommit) throw new IllegalStateException("forced second branch failure after local commit");
            return new String[] {xid, tenant, Long.toString(ProcessHandle.current().pid())};
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            throw new IllegalStateException("probe provider rejected after branch execution");
        }
    }

    private void runCaller(ApplicationConfig application) throws Exception {
        ReferenceConfig<SeataProbeRpcService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(new RegistryConfig("N/A"));
        reference.setInterface(SeataProbeRpcService.class);
        reference.setUrl("tri://[::1]:" + SeataProbeDatabase.required("FLEET_PROBE_PORT")
                + "/" + SeataProbeRpcService.class.getName());
        reference.setFilter(FILTERS);
        reference.setInjvm(false);
        reference.setRetries(0);
        reference.setTimeout(15_000);
        SeataProbeRpcService remote = reference.get();
        try {
            writeEvidence("caller-ready", properties("pid", ProcessHandle.current().pid()));
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                for (String command; (command = input.readLine()) != null;) {
                    if (command.equals("commit") || command.equals("rollback")) {
                        executeGlobal(remote, command);
                    }
                }
            }
        } finally {
            reference.destroy();
        }
    }

    private void executeGlobal(SeataProbeRpcService remote, String caseId) throws Exception {
        Properties result = properties("pid", ProcessHandle.current().pid());
        GlobalTransaction transaction = GlobalTransactionContext.getCurrentOrCreate();
        TenantContextHolder.setTenantId(tenant);
        try {
            transaction.begin(60_000, "fleet-probe-" + caseId);
            result.setProperty("xid", transaction.getXid());
            writeEvidence("caller-begin-" + caseId, result);
            writeLocally(caseId, 10);
            try (Connection connection = rawConnection()) {
                result.setProperty("amountBeforeRemote", Integer.toString(SeataProbeDatabase.value(connection, table, caseId)));
                result.setProperty("undoBeforeRemote", Integer.toString(SeataProbeDatabase.undoCount(connection, transaction.getXid())));
            }
            try {
                String[] returned = remote.write(caseId, caseId.equals("rollback"));
                result.setProperty("remoteXid", returned[0]);
                result.setProperty("remoteTenant", returned[1]);
                result.setProperty("remotePid", returned[2]);
                transaction.commit();
                result.setProperty("outcome", "committed");
            } catch (Exception remoteFailure) {
                remoteFailure.printStackTrace(System.err);
                transaction.rollback();
                result.setProperty("outcome", "rolled-back");
            }
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            result.setProperty("outcome", "failed");
            if (RootContext.getXID() != null) transaction.rollback();
        } finally {
            TenantContextHolder.clear();
            if (transaction.getXid() != null) {
                result.setProperty("status", transaction.getLocalStatus().name());
                try {
                    // TC 2.7 的 DB 模式延迟删除全局会话，默认 retry dead threshold 为 70 秒。
                    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(120).toNanos();
                    String tcStatus;
                    do {
                        tcStatus = transaction.getStatus().name();
                        if (tcStatus.equals("Finished")) break;
                        Thread.sleep(200);
                    } while (System.nanoTime() < deadline);
                    result.setProperty("tcStatus", tcStatus);
                } catch (Exception unavailable) {
                    unavailable.printStackTrace(System.err);
                    result.setProperty("tcStatus", "Unavailable");
                }
            }
            result.setProperty("contextCleared", Boolean.toString(RootContext.getXID() == null));
            writeEvidence("caller-" + caseId, result);
        }
    }

    private void writeLocally(String caseId, int increment) {
        if (!caseId.equals("commit") && !caseId.equals("rollback")) throw new IllegalArgumentException("invalid probe case");
        localTransaction.executeWithoutResult(status -> {
            int changed = jdbc.update("UPDATE public." + table + " SET amount = amount + ? WHERE id = ? AND tenant_id = ?",
                    increment, caseId, tenant);
            if (changed != 1) throw new IllegalStateException("synthetic probe row not updated");
        });
    }

    private Connection rawConnection() throws Exception {
        return SeataProbeDatabase.connect(url, username, password);
    }

    private Properties properties(Object... pairs) {
        Properties result = new Properties();
        for (int index = 0; index < pairs.length; index += 2) result.setProperty(pairs[index].toString(), pairs[index + 1].toString());
        return result;
    }

    private Properties loadProperties(Path path) throws Exception {
        Properties result = new Properties();
        try (var input = Files.newInputStream(path)) { result.load(input); }
        return result;
    }

    private void writeEvidence(String name, Properties result) throws Exception {
        Path temporary = directory.resolve(name + ".pending");
        try (OutputStream output = Files.newOutputStream(temporary)) { result.store(output, "synthetic Seata probe evidence"); }
        Files.move(temporary, directory.resolve(name + ".properties"), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
