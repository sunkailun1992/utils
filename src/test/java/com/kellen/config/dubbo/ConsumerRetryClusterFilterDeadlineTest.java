package com.kellen.config.dubbo;

import com.kellen.config.dubbo.RetryFixtures.FakeProvider;
import com.kellen.config.dubbo.RetryFixtures.Service;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.TimeoutCountDown;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.kellen.config.dubbo.RetryFixtures.elapsedMillis;
import static com.kellen.config.dubbo.RetryFixtures.unavailableAfterSend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集群层过滤器守住这次调用的超时：每次尝试拿到剩余时间，退避不睡过截止时间，上游带来的更短倒计时优先；
 * 等待时被中断立即停下；调用结束后不在线程上留下重试用的附件。
 */
class ConsumerRetryClusterFilterDeadlineTest {

    private FrameworkModel framework;

    private ApplicationModel application;

    private ModuleModel module;

    @BeforeEach
    void newModel() {
        framework = new FrameworkModel();
        application = framework.newApplication();
        module = application.getDefaultModule();
    }

    @AfterEach
    void destroyModel() {
        Thread.interrupted();
        RpcContext.removeContext();
        framework.destroy();
    }

    @Test
    void deadlineBoundsTheTotalTime() {
        FakeProvider provider = neverAvailable();
        long start = System.nanoTime();

        assertThatThrownBy(() -> cluster(consumer("timeout", "300"), provider).invoke(RetryFixtures.invocation("write")))
                .isInstanceOf(RpcException.class);
        assertThat(elapsedMillis(start)).isBetween(100L, 450L);
    }

    @Test
    void upstreamCountdownBoundsTheDeadline() {
        FakeProvider provider = neverAvailable();
        // ConsumerContextFilter 会把上游请求带来的倒计时从服务端附件带进客户端附件。
        RpcContext.getServerAttachment().setObjectAttachment(
                CommonConstants.TIME_COUNTDOWN_KEY, TimeoutCountDown.newCountDown(200, TimeUnit.MILLISECONDS));
        long start = System.nanoTime();

        assertThatThrownBy(() -> cluster(consumer("timeout", "3000"), provider).invoke(RetryFixtures.invocation("write")))
                .isInstanceOf(RpcException.class);
        assertThat(elapsedMillis(start)).isBetween(100L, 350L);
    }

    @Test
    void eachAttemptGetsTheRemainingTime() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true")).thenThrow(unavailableAfterSend());

        cluster(consumer("timeout", "3000"), provider).invoke(RetryFixtures.invocation("read"));

        List<Long> seen = provider.countdownsSeen();
        assertThat(seen).hasSize(2).doesNotContainNull();
        assertThat(seen).allSatisfy(remaining -> assertThat(remaining).isBetween(1L, 2999L));
        assertThat(seen.get(1)).isLessThan(seen.get(0));
    }

    @Test
    void backoffNeverOvershootsTheDeadline() {
        FakeProvider provider = neverAvailable();
        long start = System.nanoTime();

        assertThatThrownBy(() -> cluster(consumer("timeout", "150"), provider).invoke(RetryFixtures.invocation("write")))
                .isInstanceOf(RpcException.class);
        assertThat(elapsedMillis(start)).isBetween(100L, 200L);
    }

    @Test
    void interruptDuringBackoffStopsAndKeepsTheFlag() {
        FakeProvider provider = neverAvailable();
        Thread caller = Thread.currentThread();
        ScheduledExecutorService interrupter = Executors.newSingleThreadScheduledExecutor();
        try {
            interrupter.schedule(caller::interrupt, 50, TimeUnit.MILLISECONDS);
            long start = System.nanoTime();

            assertThatThrownBy(() -> cluster(consumer("timeout", "3000"), provider).invoke(RetryFixtures.invocation("write")))
                    .isInstanceOf(RpcException.class)
                    .hasStackTraceContaining("请求未发出");
            long elapsed = elapsedMillis(start);

            assertThat(Thread.interrupted()).isTrue();
            assertThat(elapsed).isBetween(50L, 299L);
        } finally {
            interrupter.shutdownNow();
        }
    }

    @Test
    void nothingLeaksIntoTheNextCallOnTheSameThread() {
        FakeProvider recovers = new FakeProvider(provider(31001, "read.idempotent", "true")).thenThrow(unavailableAfterSend());
        cluster(consumer(), recovers).invoke(RetryFixtures.invocation("read"));
        assertNoRetryAttachments();

        FakeProvider fails = new FakeProvider(provider(31002, "read.idempotent", "true"))
                .thenThrow(unavailableAfterSend(), unavailableAfterSend(), unavailableAfterSend());
        assertThatThrownBy(() -> cluster(consumer(), fails).invoke(RetryFixtures.invocation("read")))
                .isInstanceOf(RpcException.class);
        assertNoRetryAttachments();
    }

    private static void assertNoRetryAttachments() {
        assertThat(RpcContext.getClientAttachment().getObjectAttachment(CommonConstants.RETRIES_KEY)).isNull();
        assertThat(RpcContext.getClientAttachment().getObjectAttachment(CommonConstants.TIME_COUNTDOWN_KEY)).isNull();
    }

    private FakeProvider neverAvailable() {
        return new FakeProvider(provider(31001)).availableAfter(Duration.ofHours(1));
    }

    private URL consumer(String... keyValues) {
        String[] parameters = new String[keyValues.length + 4];
        parameters[0] = "timeout";
        parameters[1] = "3000";
        parameters[2] = "retries";
        parameters[3] = "0";
        System.arraycopy(keyValues, 0, parameters, 4, keyValues.length);
        return RetryFixtures.consumerUrl(module, parameters);
    }

    private URL provider(int port, String... keyValues) {
        return RetryFixtures.providerUrl(module, port, keyValues);
    }

    @SafeVarargs
    private Invoker<Service> cluster(URL consumer, Invoker<Service>... providers) {
        return RetryFixtures.cluster(application, consumer, providers);
    }
}
