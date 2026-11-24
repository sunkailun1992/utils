package com.kellen.config.dubbo;

import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.kellen.config.dubbo.RetryFixtures.elapsedMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 Triple 连接下的两层重试：提供方与调用端都在本进程，调用端经 {@link InterruptingTcpProxy} 连到提供方。
 *
 * <p>引用不改 Dubbo 默认的 {@code retries}（失败转移再试 2 次），由集群层过滤器在每次尝试里设为 0；{@code read} 在引用上
 * 声明幂等，{@code write} 不声明。“转发请求后复位”由提供方在执行方法时调用代理的 {@link InterruptingTcpProxy#resetAll()}：
 * 请求已到提供方、响应还没写出。每个用例新建、结束即销毁 {@link FrameworkModel}。</p>
 */
class ConsumerRetryTripleIntegrationTest {

    private final RetryProbeProvider provider = new RetryProbeProvider();

    private final List<ReferenceConfig<RetryProbeService>> references = new ArrayList<>();

    private FrameworkModel framework;

    private ApplicationModel application;

    private ServiceConfig<RetryProbeService> service;

    private InterruptingTcpProxy proxy;

    private ReferenceConfig<RetryProbeService> reference;

    private RetryProbeService probe;

    @BeforeEach
    void connectThroughTheProxy() throws Exception {
        framework = new FrameworkModel();
        application = framework.defaultApplication();
        // 只测 Triple RPC：不经 Spring 启动的 Dubbo 拿不到 Spring 上下文，Triple 的 Spring REST 适配器会导出失败
        // （同 FleetSecurityContextDroppedConnectionTest）。
        application.modelEnvironment().updateAppConfigMap(Map.of("dubbo.protocol.triple.rest.enabled", "false"));
        ApplicationConfig applicationConfig = new ApplicationConfig("retry-triple-test");
        applicationConfig.setQosEnable(false);
        applicationConfig.setShutwait("100");
        application.getApplicationConfigManager().setApplication(applicationConfig);
        ModuleModel module = application.newModule();

        service = new ServiceConfig<>(module);
        service.setInterface(RetryProbeService.class);
        service.setRef(provider);
        service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
        ProtocolConfig protocol = new ProtocolConfig("tri", -1);
        protocol.setHost(InetAddress.getLoopbackAddress().getHostAddress());
        service.setProtocol(protocol);
        service.export();

        // Dubbo 连本机回环地址时会换成本机地址（NetUtils#filterLocalHost），代理就监听那个地址。
        proxy = InterruptingTcpProxy.start(InetAddress.getByName(NetUtils.getLocalHost()),
                service.getExportedUrls().get(0).getPort());
        reference = reference(module, 3000);
        probe = reference.get();

        assertThat(probe.write("warm")).isEqualTo("write:warm");
        assertThat(proxy.acceptedConnections()).isEqualTo(1);
    }

    @AfterEach
    void stopDubboAndProxy() throws Exception {
        references.forEach(ReferenceConfig::destroy);
        service.unexport();
        proxy.close();
        framework.destroy();
    }

    @Test
    void undeclaredCallRightAfterAResetWaitsForTheReconnect() {
        Invoker<?> connection = connectionInvoker();
        proxy.resetAll();
        awaitUntil(() -> !connection.isAvailable(), Duration.ofSeconds(2));

        assertThat(probe.write("a")).isEqualTo("write:a");
        assertThat(proxy.acceptedConnections()).isEqualTo(2);
        assertThat(provider.calls("write")).isEqualTo(2);
    }

    @Test
    void callWhileTheProviderIsOutOfTheAvailableListWaitsForTheRecheck() {
        proxy.resetAll();
        // 经目录问可用性，与请求选路一样会把不可用的提供方移出可用列表。
        awaitUntil(() -> !reference.getInvoker().isAvailable(), Duration.ofSeconds(2));

        assertThat(probe.write("a")).isEqualTo("write:a");
        assertThat(proxy.acceptedConnections()).isEqualTo(2);
        assertThat(provider.calls("write")).isEqualTo(2);
    }

    @Test
    void idempotentCallResetAfterTheRequestIsRetried() {
        provider.onNextCall(proxy::resetAll);

        assertThat(probe.read("a")).isEqualTo("read:a");
        assertThat(provider.calls("read")).isEqualTo(2);
    }

    @Test
    void undeclaredCallResetAfterTheRequestFailsOnce() {
        provider.onNextCall(proxy::resetAll);

        assertThatThrownBy(() -> probe.write("a")).isInstanceOf(RpcException.class);
        assertThat(provider.calls("write")).as("预热 1 次加这次 1 次").isEqualTo(2);
    }

    @Test
    void timeoutIsNotRetried() {
        RetryProbeService shortTimeout = reference(application.newModule(), 500).get();
        long start = System.nanoTime();

        assertThatThrownBy(() -> shortTimeout.read("slow"))
                .isInstanceOfSatisfying(RpcException.class, failure -> assertThat(failure.isTimeout()).isTrue());
        assertThat(elapsedMillis(start)).isLessThan(1000);
        assertThat(provider.calls("read")).isEqualTo(1);
    }

    @Test
    void businessExceptionIsNotRetried() {
        assertThatThrownBy(() -> probe.read("boom")).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThat(provider.calls("read")).isEqualTo(1);
    }

    @Test
    void asyncCallFailsWithoutWaitingForTheReconnect() {
        Invoker<?> connection = connectionInvoker();
        proxy.resetAll();
        awaitUntil(() -> !connection.isAvailable(), Duration.ofSeconds(2));
        long start = System.nanoTime();

        CompletableFuture<String> future = probe.readAsync("a");

        assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS)).hasCauseInstanceOf(RpcException.class);
        assertThat(elapsedMillis(start)).isLessThan(500);
    }

    @Test
    void providerGoneForGoodFailsAtTheDeadline() throws Exception {
        RetryProbeService shortTimeout = reference(application.newModule(), 1000).get();
        assertThat(shortTimeout.write("warm")).isEqualTo("write:warm");
        Invoker<?> connection = connectionInvoker();
        proxy.close();
        awaitUntil(() -> !connection.isAvailable(), Duration.ofSeconds(2));
        long start = System.nanoTime();

        assertThatThrownBy(() -> shortTimeout.write("a")).isInstanceOf(RpcException.class);
        assertThat(elapsedMillis(start)).isBetween(500L, 1150L);
    }

    private ReferenceConfig<RetryProbeService> reference(ModuleModel module, int timeoutMillis) {
        ReferenceConfig<RetryProbeService> created = new ReferenceConfig<>(module);
        created.setInterface(RetryProbeService.class);
        created.setUrl("tri://" + NetUtils.getLocalHost() + ":" + proxy.port() + "/" + RetryProbeService.class.getName());
        created.setScope("remote");
        created.setCheck(false);
        created.setLazy(true);
        created.setTimeout(timeoutMillis);
        MethodConfig read = new MethodConfig();
        read.setName("read");
        read.setParameters(Map.of(DubboRetryPolicy.IDEMPOTENT_KEY, "true"));
        created.setMethods(List.of(read));
        references.add(created);
        return created;
    }

    /**
     * @return 主引用目录里唯一的 Triple 调用端（问它的可用性不经目录，没有副作用）
     */
    private Invoker<?> connectionInvoker() {
        assertThat(reference.getInvoker()).isInstanceOf(ClusterInvoker.class);
        List<? extends Invoker<?>> invokers = ((ClusterInvoker<?>) reference.getInvoker()).getDirectory().getAllInvokers();
        assertThat(invokers).hasSize(1);
        return invokers.get(0);
    }

    private static void awaitUntil(BooleanSupplier condition, Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("等待条件成立超时").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }
}
