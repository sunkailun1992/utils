package com.kellen.config.dubbo;

import com.kellen.config.web.ReqConfig;
import com.kellen.traffic.CanaryContext;
import com.kellen.traffic.CanaryGrant;
import com.kellen.traffic.TrafficGovernanceProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.config.spring.extension.SpringExtensionInjector;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按 Dubbo 运行时的方式创建 {@link DubboContextPropagationFilter}，验证它拿到的是 Spring 绑定好的配置。
 *
 * <p>Dubbo 创建扩展后按 setter 名到 Spring 容器里取 Bean，不按类型找；直接 new 过滤器再调 setter 的单元测试覆盖不到这条路径。</p>
 */
class DubboContextPropagationFilterInjectionTest {

    private static final String SIGNING_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceWiring.class)
            .withPropertyValues(
                    "traffic.governance.canary.enabled=true",
                    "traffic.governance.canary.require-grant=true",
                    "traffic.governance.canary.signing-key=" + SIGNING_KEY,
                    "traffic.governance.canary.rpc-sources[java.lang.Object]=report",
                    "tenant.header-names=X-Kellen-Tenant");

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        CanaryContext.clear();
    }

    @Test
    void injectedFilterAcceptsGrantSignedWithConfiguredKey() {
        runner.run(context -> withDubboFilter(context, filter -> {
            RpcInvocation invocation = invocation();
            invocation.setAttachment(CanaryGrant.ATTACHMENT,
                    CanaryGrant.issue("report", "gray", Instant.now().plusSeconds(30), SIGNING_KEY));

            assertThat(filter.invoke(invoker(CommonConstants.PROVIDER, "gray"), invocation).getValue())
                    .isEqualTo("ok");
        }));
    }

    @Test
    void injectedFilterRejectsCallWithoutGrantWhenGrantRequired() {
        runner.run(context -> withDubboFilter(context, filter ->
                assertThatThrownBy(() -> filter.invoke(invoker(CommonConstants.PROVIDER, "gray"), invocation()))
                        .isInstanceOf(RpcException.class)
                        .hasMessageContaining("requires a grant")));
    }

    @Test
    void injectedFilterAttachesTenantUnderConfiguredHeaderNames() {
        runner.run(context -> withDubboFilter(context, filter -> {
            TenantContextHolder.setTenantId("1001");
            RpcInvocation invocation = invocation();

            filter.invoke(invoker(CommonConstants.CONSUMER, null), invocation);

            assertThat(invocation.getAttachment("X-Kellen-Tenant")).isEqualTo("1001");
            assertThat(invocation.getAttachment("tenantId")).isNull();
        }));
    }

    @Test
    void ambiguousPropertiesBeansAreNotAliased() {
        new ApplicationContextRunner()
                .withUserConfiguration(DubboExtensionPropertiesAliasConfig.class, TwoTrafficGovernanceBeans.class)
                .run(context -> assertThat(context.containsBean("trafficGovernanceProperties")).isFalse());
    }

    /**
     * 与 Dubbo 的 {@code DubboContextPostProcessor} 一致：应用级、模块级注入器都绑定 Spring 容器，再由扩展加载器创建过滤器。
     */
    private static void withDubboFilter(ApplicationContext context, Consumer<Filter> assertions) {
        FrameworkModel framework = new FrameworkModel();
        try {
            ApplicationModel application = framework.newApplication();
            ModuleModel module = application.newModule();
            SpringExtensionInjector.get(application).init(context);
            SpringExtensionInjector.get(module).init(context);
            assertions.accept(module.getExtensionLoader(Filter.class).getExtension("kellenContextPropagation"));
        } finally {
            framework.destroy();
        }
    }

    private static RpcInvocation invocation() {
        return new RpcInvocation("test", "TestService", "", new Class<?>[0], new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private static Invoker<Object> invoker(String side, String tag) {
        URL url = URL.valueOf("tri://127.0.0.1:20880/com.kellen.TestService")
                .addParameter(CommonConstants.SIDE_KEY, side);
        Invoker<Object> invoker = mock(Invoker.class);
        when(invoker.getUrl()).thenReturn(tag == null ? url : url.addParameter(CommonConstants.TAG_KEY, tag));
        when(invoker.getInterface()).thenReturn(Object.class);
        when(invoker.invoke(any())).thenReturn(new AppResponse("ok"));
        return invoker;
    }

    /**
     * 模拟业务服务的装配：{@link ReqConfig} 注册配置属性，Dubbo 相关配置随业务服务对 {@code com.kellen} 的组件扫描进入容器。
     */
    @Configuration(proxyBeanMethods = false)
    @Import(ReqConfig.class)
    @ComponentScan(basePackageClasses = DubboContextPropagationFilter.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    static class ServiceWiring {
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoTrafficGovernanceBeans {

        @Bean
        TrafficGovernanceProperties primaryTrafficGovernance() {
            return new TrafficGovernanceProperties();
        }

        @Bean
        TrafficGovernanceProperties secondaryTrafficGovernance() {
            return new TrafficGovernanceProperties();
        }
    }
}
