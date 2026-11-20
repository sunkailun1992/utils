package com.kellen.security;

import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.threadpool.TtlExecutors;
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
 * 用户与租户上下文不向新建线程继承：新线程读不到建它的线程的上下文；只有经 TtlRunnable、TtlExecutors 包装的任务才传递。
 */
class ContextInheritanceTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
        TenantContextHolder.clear();
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
