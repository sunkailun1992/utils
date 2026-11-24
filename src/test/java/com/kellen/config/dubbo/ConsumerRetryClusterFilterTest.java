package com.kellen.config.dubbo;

import com.kellen.config.dubbo.RetryFixtures.EmptyDirectory;
import com.kellen.config.dubbo.RetryFixtures.FakeProvider;
import com.kellen.config.dubbo.RetryFixtures.Service;
import com.kellen.traffic.CanaryContext;
import com.kellen.traffic.CanaryGrant;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.StatusRpcException;
import org.apache.dubbo.rpc.TriRpcStatus;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.kellen.config.dubbo.RetryFixtures.elapsedMillis;
import static com.kellen.config.dubbo.RetryFixtures.unavailableAfterSend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集群层过滤器的两层重试与失败分类：未发出的对任何方法都重试，发出后的连接类失败只对声明了幂等的方法重试，
 * 其余不重试；每次尝试 Dubbo 自己只试一次。
 *
 * <p>调用端 URL 默认 {@code retries=0}，让 Dubbo 自己的失败转移盖不住要测的行为；不该等、不该重试的用例
 * 在过滤器出现之前就能通过，它们是防止过滤器多做事的护栏。</p>
 */
class ConsumerRetryClusterFilterTest {

    private static final String CANARY_TAG = "retry-canary";

    private static final String GRANT_TOKEN = "retry-test-grant";

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
        CanaryContext.clear();
        RpcContext.removeContext();
        framework.destroy();
    }

    @Test
    void unprocessedCallWaitsUntilTheProviderIsBack() {
        FakeProvider provider = new FakeProvider(provider(31001)).availableAfter(Duration.ofMillis(300));

        Result result = cluster(consumer(), provider).invoke(RetryFixtures.invocation("write"));

        assertThat(result.getValue()).isEqualTo("ok:write");
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void noProviderFailsFast() {
        Invoker<Service> cluster = RetryFixtures.join(new EmptyDirectory(consumer().setScopeModel(module)));
        long start = System.nanoTime();

        assertThatThrownBy(() -> cluster.invoke(RetryFixtures.invocation("read")))
                .isInstanceOfSatisfying(RpcException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER));
        assertThat(elapsedMillis(start)).isLessThan(500);
    }

    @Test
    void canaryTagWithoutMatchingProviderFailsFast() {
        FakeProvider stable = new FakeProvider(provider(31001));
        Invoker<Service> cluster = cluster(consumer("traffic.canary.enabled", "true"), stable);
        bindCanary();
        long start = System.nanoTime();

        assertThatThrownBy(() -> cluster.invoke(RetryFixtures.invocation("read")))
                .isInstanceOfSatisfying(RpcException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER));
        assertThat(elapsedMillis(start)).isLessThan(500);
        assertThat(stable.calls()).isZero();
    }

    @Test
    void retriesKeepTheCanaryGrantAndRouting() {
        FakeProvider stable = new FakeProvider(provider(31001));
        AtomicReference<String> grantSeen = new AtomicReference<>();
        FakeProvider canary = new FakeProvider(provider(31002, CommonConstants.TAG_KEY, CANARY_TAG))
                .availableAfter(Duration.ofMillis(300))
                .thenReturn(invocation -> {
                    grantSeen.set(invocation.getAttachment(CanaryGrant.ATTACHMENT));
                    return AsyncRpcResult.newDefaultAsyncResult("canary", invocation);
                });
        Invoker<Service> cluster = cluster(consumer("traffic.canary.enabled", "true"), stable, canary);
        bindCanary();

        Result result = cluster.invoke(RetryFixtures.invocation("read"));

        assertThat(result.getValue()).isEqualTo("canary");
        assertThat(grantSeen.get()).isEqualTo(GRANT_TOKEN);
        assertThat(stable.calls()).isZero();
    }

    @Test
    void idempotentMethodIsRetriedAfterASentFailure() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true")).thenThrow(unavailableAfterSend());

        Result result = cluster(consumer(), provider).invoke(RetryFixtures.invocation("read"));

        assertThat(result.getValue()).isEqualTo("ok:read");
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    void undeclaredMethodIsNotRetriedAfterASentFailure() {
        FakeProvider provider = new FakeProvider(provider(31001)).thenThrow(unavailableAfterSend());

        assertThatThrownBy(() -> cluster(consumer(), provider).invoke(RetryFixtures.invocation("read")))
                .isInstanceOf(RpcException.class)
                .hasRootCauseInstanceOf(StatusRpcException.class);
        assertThat(provider.calls()).isEqualTo(1);
    }

    /**
     * Triple 请求发出后的失败不抛出，而是放在结果里：状态异常写进 AppResponse，调用端 RpcExceptionFilter 在结果监听里重抛，
     * 结果的 future 以异常结束（真实连接下见 ConsumerRetryTripleIntegrationTest 的复位用例）。
     */
    @Test
    void idempotentMethodIsRetriedWhenTheFailureComesBackInTheResult() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"))
                .thenReturn(invocation -> AsyncRpcResult.newDefaultAsyncResult(TriRpcStatus.CANCELLED.asException(), invocation));

        Result result = cluster(consumer(), provider).invoke(RetryFixtures.invocation("read"));

        assertThat(result.getValue()).isEqualTo("ok:read");
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    void undeclaredMethodGetsTheFailedResultBackAsIs() {
        FakeProvider provider = new FakeProvider(provider(31001))
                .thenReturn(invocation -> AsyncRpcResult.newDefaultAsyncResult(TriRpcStatus.CANCELLED.asException(), invocation));

        Result result = cluster(consumer(), provider).invoke(RetryFixtures.invocation("read"));

        assertThatThrownBy(result::recreate).isInstanceOf(RpcException.class).hasRootCauseInstanceOf(StatusRpcException.class);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void sentRetriesStopAfterThreeAttempts() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"))
                .thenThrow(unavailableAfterSend(), unavailableAfterSend(), unavailableAfterSend(), unavailableAfterSend());

        assertThatThrownBy(() -> cluster(consumer(), provider).invoke(RetryFixtures.invocation("read")))
                .isInstanceOf(RpcException.class);
        assertThat(provider.calls()).isEqualTo(3);
    }

    @Test
    void exhaustedBudgetStopsSentRetries() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"));
        for (int failure = 0; failure < 6; failure++) {
            provider.thenThrow(unavailableAfterSend());
        }
        Invoker<Service> cluster = cluster(consumer(), provider);
        // 5 次不幂等的调用各失败一次，令牌从 10 降到 5，此后不再允许第二层重试。
        for (int call = 0; call < 5; call++) {
            assertThatThrownBy(() -> cluster.invoke(RetryFixtures.invocation("write"))).isInstanceOf(RpcException.class);
        }

        assertThatThrownBy(() -> cluster.invoke(RetryFixtures.invocation("read"))).isInstanceOf(RpcException.class);
        assertThat(provider.calls()).isEqualTo(6);
    }

    @Test
    void timeoutIsNotRetried() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"))
                .thenThrow(new RpcException(RpcException.TIMEOUT_EXCEPTION, "x"));

        assertThatThrownBy(() -> cluster(consumer(), provider).invoke(RetryFixtures.invocation("read")))
                .isInstanceOf(RpcException.class);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void businessExceptionIsReturnedAsIs() {
        IllegalStateException business = new IllegalStateException("biz");
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"))
                .thenReturn(invocation -> AsyncRpcResult.newDefaultAsyncResult(business, invocation));

        Result result = cluster(consumer(), provider).invoke(RetryFixtures.invocation("read"));

        assertThat(result.hasException()).isTrue();
        assertThat(result.getException()).isSameAs(business);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void asyncCallIsPassedThrough() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true")).thenThrow(unavailableAfterSend());
        RpcInvocation invocation = RetryFixtures.invocation("read");
        invocation.setInvokeMode(InvokeMode.ASYNC);
        Invoker<Service> cluster = cluster(consumer(), provider);
        long start = System.nanoTime();

        assertThatThrownBy(() -> cluster.invoke(invocation)).isInstanceOf(RpcException.class);
        assertThat(elapsedMillis(start)).isLessThan(500);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void filterIsInnermostAmongClusterFilters() {
        List<ClusterFilter> filters = module.getExtensionLoader(ClusterFilter.class).getActivateExtension(
                consumer("traffic.canary.enabled", "true"), CommonConstants.REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);

        assertThat(filters).extracting(filter -> filter.getClass().getSimpleName())
                .contains("ConsumerContextFilter", "DubboCanaryClusterFilter");
        assertThat(filters.get(filters.size() - 1)).isInstanceOf(ConsumerRetryClusterFilter.class);
    }

    @Test
    void dubboFailoverTriesOnlyOncePerAttempt() {
        FakeProvider provider = new FakeProvider(provider(31001)).thenThrow(unavailableAfterSend());
        // 不写 retries，即 Dubbo 默认的失败转移再试 2 次。
        URL withDubboDefaultRetries = RetryFixtures.consumerUrl(module, "timeout", "3000");

        assertThatThrownBy(() -> cluster(withDubboDefaultRetries, provider).invoke(RetryFixtures.invocation("write")))
                .isInstanceOf(RpcException.class);
        assertThat(provider.calls()).isEqualTo(1);
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

    private static void bindCanary() {
        CanaryGrant grant = new CanaryGrant("retry-test-provider", CANARY_TAG, Instant.now().plusSeconds(120));
        CanaryContext.set(new CanaryContext.Snapshot(grant, GRANT_TOKEN));
    }
}
