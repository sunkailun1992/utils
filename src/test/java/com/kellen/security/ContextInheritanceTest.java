package com.kellen.security;

import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.kellen.datapermission.DataPermissionContextHolder;
import com.kellen.traffic.TrafficGovernanceContext;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 线程上下文不向新建线程继承：用户、租户、数据源标记、忽略数据权限标记与流量治理快照，新线程都读不到建它的线程的值；
 * 只有经 TtlRunnable、TtlExecutors 包装的任务才传递。
 */
class ContextInheritanceTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
        TenantContextHolder.clear();
        DynamicSourceTtl.clear();
        DataPermissionContextHolder.clear();
        TrafficGovernanceContext.clear();
    }

    @Test
    void newThreadDoesNotInheritUser() throws InterruptedException {
        UserContextHolder.set(new SecurityUser("u1", "alice", "t1", List.of()));
        AtomicReference<Object> seen = new AtomicReference<>("未执行");

        runInNewThread(() -> seen.set(UserContextHolder.get()));

        assertThat(seen.get()).isNull();
    }

    @Test
    void newThreadDoesNotInheritTenant() throws InterruptedException {
        TenantContextHolder.setTenantId("t1");
        TenantContextHolder.ignore();
        AtomicReference<Object> tenant = new AtomicReference<>("未执行");
        AtomicReference<Boolean> ignore = new AtomicReference<>();

        runInNewThread(() -> {
            tenant.set(TenantContextHolder.getTenantId());
            ignore.set(TenantContextHolder.isIgnore());
        });

        assertThat(tenant.get()).isNull();
        assertThat(ignore.get()).isFalse();
    }

    @Test
    void newThreadDoesNotInheritDataSource() throws InterruptedException {
        DynamicSourceTtl.push(DynamicSourceTtl.SLAVE_DATASOURCE);
        AtomicReference<Object> seen = new AtomicReference<>("未执行");

        runInNewThread(() -> seen.set(DynamicSourceTtl.peek()));

        assertThat(seen.get()).isNull();
    }

    @Test
    void newThreadDoesNotInheritDataPermissionIgnore() throws InterruptedException {
        DataPermissionContextHolder.ignore();
        AtomicReference<Boolean> ignore = new AtomicReference<>();

        runInNewThread(() -> ignore.set(DataPermissionContextHolder.isIgnore()));

        assertThat(ignore.get()).isFalse();
    }

    @Test
    void newThreadDoesNotInheritTrafficSnapshot() throws InterruptedException {
        TrafficGovernanceContext.set(new TrafficGovernanceContext.Snapshot("v2", "canary", "kellen-local", null));
        AtomicReference<Object> seen = new AtomicReference<>("未执行");

        runInNewThread(() -> seen.set(TrafficGovernanceContext.get()));

        assertThat(seen.get()).isNull();
    }

    @Test
    void ttlRunnableStillPropagatesDataSourceAndTraffic() throws InterruptedException {
        TrafficGovernanceContext.Snapshot snapshot = new TrafficGovernanceContext.Snapshot("v2", "canary", "kellen-local", null);
        DynamicSourceTtl.push(DynamicSourceTtl.SLAVE_DATASOURCE);
        TrafficGovernanceContext.set(snapshot);
        AtomicReference<Object> dataSource = new AtomicReference<>("未执行");
        AtomicReference<Object> traffic = new AtomicReference<>("未执行");

        runInNewThread(TtlRunnable.get(() -> {
            dataSource.set(DynamicSourceTtl.peek());
            traffic.set(TrafficGovernanceContext.get());
        }));

        assertThat(dataSource.get()).isEqualTo(DynamicSourceTtl.SLAVE_DATASOURCE);
        assertThat(traffic.get()).isSameAs(snapshot);
    }

    @Test
    void ttlRunnableStillPropagatesUser() throws InterruptedException {
        SecurityUser user = new SecurityUser("u1", "alice", "t1", List.of());
        UserContextHolder.set(user);
        AtomicReference<Object> seen = new AtomicReference<>("未执行");

        runInNewThread(TtlRunnable.get(() -> seen.set(UserContextHolder.get())));

        assertThat(seen.get()).isSameAs(user);
    }

    @Test
    void ttlExecutorsStillPropagateTenant() throws Exception {
        ExecutorService pool = TtlExecutors.getTtlExecutorService(Executors.newSingleThreadExecutor());
        try {
            pool.submit(() -> { }).get(5, TimeUnit.SECONDS); // 先建好工作线程，结果就不可能来自建线程时的继承。
            TenantContextHolder.setTenantId("t1");

            assertThat(pool.submit(TenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS)).isEqualTo("t1");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void runInNewThread(Runnable task) throws InterruptedException {
        Thread thread = new Thread(task, "context-inheritance-test");
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(thread.isAlive()).isFalse();
    }
}
