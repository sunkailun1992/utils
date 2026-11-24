package com.kellen.security.oauth2;

import com.kellen.config.dubbo.InterruptingTcpProxy;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C0110 的机制复现：调用端到 user 的 Triple 连接被中间设备断开后，第一次取业务上下文不能直接按 user 不可用处理（503）。
 *
 * <p>提供方与调用端都在本进程，但走真实的 Triple 连接：调用端连 {@link InterruptingTcpProxy}，代理再转给提供方。
 * Dubbo 用每个测试新建、结束即销毁的 {@link FrameworkModel}，不碰默认模型，测试之间不共享连接、端口与引用。</p>
 *
 * <p>Dubbo 3.3.7 里，Triple 调用端在连接没连上时直接失败、不等重连（{@code TripleInvoker#doInvoke}），连接断开 1 秒后才重连
 * （{@code NettyConnectionHandler#reconnect}）；选路发现提供方不可用还会把它移出可用列表，每秒复查一次
 * （{@code AbstractDirectory#addInvalidateInvoker}），复查前的请求得到“没有可用提供方”。11-22 的 C0110 是首轮请求约 86 毫秒
 * 全部失败、约 1 秒后恢复，与此吻合。</p>
 */
class FleetSecurityContextDroppedConnectionTest {

    private final StubSecurityContextRpcService provider = new StubSecurityContextRpcService();

    private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-1").claim("user_id", "u1").build();

    private FrameworkModel framework;

    private ServiceConfig<SecurityContextRpcService> service;

    private ReferenceConfig<SecurityContextRpcService> reference;

    private InterruptingTcpProxy proxy;

    private FleetJwtAuthenticationConverter converter;

    @BeforeEach
    void connectThroughTheProxy() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        framework = new FrameworkModel();
        ApplicationModel application = framework.defaultApplication();
        // 官方开关 dubbo.protocol.triple.rest.enabled=false，只测 Triple RPC：Triple 协议创建时从本模型默认应用的全局配置读它
        // （TripleProtocol 的静态字段，进程内各模型共用；本类之外的 utils 测试不在 Dubbo 里导出 Triple 服务，不受影响）。
        // 不经 Spring 启动的 Dubbo 拿不到 Spring 上下文，3.3.7 的 Spring REST 适配器会调用 Spring 7 已移除的
        // PropertyPlaceholderHelper 构造方法而导出失败（同 docs/seata-live-contract.md 的探针）。
        application.modelEnvironment().updateAppConfigMap(Map.of("dubbo.protocol.triple.rest.enabled", "false"));
        ApplicationConfig applicationConfig = new ApplicationConfig("fleet-dropped-connection-test");
        applicationConfig.setQosEnable(false);
        // 测试结束时不等在途请求（默认优雅停机最长等 10 秒）。
        applicationConfig.setShutwait("100");
        application.getApplicationConfigManager().setApplication(applicationConfig);
        ModuleModel module = application.newModule();

        service = new ServiceConfig<>(module);
        service.setInterface(SecurityContextRpcService.class);
        service.setRef(provider);
        service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
        ProtocolConfig protocol = new ProtocolConfig("tri", -1);
        protocol.setHost("127.0.0.1");
        service.setProtocol(protocol);
        service.export();

        // Dubbo 连本机回环地址时会换成本机地址（NetUtils#filterLocalHost），代理就监听那个地址，再转给只绑回环地址的提供方。
        String localHost = NetUtils.getLocalHost();
        proxy = InterruptingTcpProxy.start(InetAddress.getByName(localHost), service.getExportedUrls().get(0).getPort());
        reference = new ReferenceConfig<>(module);
        reference.setInterface(SecurityContextRpcService.class);
        reference.setUrl("tri://" + localHost + ":" + proxy.port() + "/" + SecurityContextRpcService.class.getName());
        reference.setScope("remote");
        // 与 FleetSecurityContextDubboAutoConfiguration 的引用一致：不检查提供方、懒连接、3 秒超时、Dubbo 自己不重试。
        reference.setCheck(false);
        reference.setLazy(true);
        reference.setTimeout(3000);
        reference.setRetries(0);

        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("securityContextRpcService", reference.get());
        converter = new FleetJwtAuthenticationConverter(new FleetOAuth2Properties(), new SecurityUserCache(null, Duration.ZERO),
                beans.getBeanProvider(SecurityContextRpcService.class));

        assertThat(statusOf(converter.convert(jwt))).isEqualTo(SecurityContextResult.Status.OK);
        assertThat(proxy.acceptedConnections()).isEqualTo(1);
    }

    @AfterEach
    void stopDubboAndProxy() throws Exception {
        reference.destroy();
        service.unexport();
        proxy.close();
        framework.destroy();
    }

    /**
     * 连接断开后的第一次查询：Triple 连接不可用（UNAVAILABLE），选路同时把提供方移出可用列表，之后是“没有可用提供方”，
     * 直到 1 秒后重连、目录复查把它放回来。
     */
    @Test
    void firstLookupAfterTheConnectionDropsWaitsForTheReconnect() {
        Invoker<?> connection = connectionInvoker();
        proxy.resetAll();
        // 等调用端收到复位、知道连接没了（重连要等 1 秒）。直接问 Triple 调用端，不经目录，不会把提供方移出可用列表。
        awaitUntil(() -> !connection.isAvailable(), Duration.ofSeconds(2));

        assertThat(statusOf(converter.convert(jwt))).isEqualTo(SecurityContextResult.Status.OK);
        assertThat(proxy.acceptedConnections()).isEqualTo(2);
        assertThat(provider.calls()).hasSize(2);
    }

    /**
     * 提供方已被移出可用列表时的查询：目录每秒复查一次，第一次复查若早于重连完成，要到下一次复查（约 2 秒）才恢复，
     * 重试的总等待要盖住这一段。
     */
    @Test
    void lookupWhileTheProviderIsOutOfTheAvailableListWaitsForTheRecheck() {
        proxy.resetAll();
        // 经目录问可用性，与请求选路一样会把不可用的提供方移出可用列表。
        awaitUntil(() -> !reference.getInvoker().isAvailable(), Duration.ofSeconds(2));

        assertThat(statusOf(converter.convert(jwt))).isEqualTo(SecurityContextResult.Status.OK);
        assertThat(proxy.acceptedConnections()).isEqualTo(2);
        assertThat(provider.calls()).hasSize(2);
    }

    /**
     * @return 引用目录里唯一的 Triple 调用端（问它的可用性不经目录，没有副作用）
     */
    private Invoker<?> connectionInvoker() {
        assertThat(reference.getInvoker()).isInstanceOf(ClusterInvoker.class);
        List<? extends Invoker<?>> invokers = ((ClusterInvoker<?>) reference.getInvoker()).getDirectory().getAllInvokers();
        assertThat(invokers).hasSize(1);
        return invokers.get(0);
    }

    private static SecurityContextResult.Status statusOf(AbstractAuthenticationToken token) {
        assertThat(token).isInstanceOf(FleetAuthenticationToken.class);
        return ((FleetAuthenticationToken) token).getStatus();
    }

    private static void awaitUntil(BooleanSupplier condition, Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("等待条件成立超时").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }
}
