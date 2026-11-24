package com.kellen.config.dubbo;

import com.kellen.config.dubbo.RetryFixtures.FakeProvider;
import com.kellen.config.dubbo.RetryFixtures.Service;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.InstanceAddressURL;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.filter.DefaultFilterChainBuilder;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 协议层过滤器：同步调用遇到不可用的提供方不发请求、标记“未发出”，并记下本次提供方的幂等声明。
 */
class UnprocessedRequestFilterTest {

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
        RpcContext.removeContext();
        framework.destroy();
    }

    @Test
    void unavailableProviderIsNotInvokedAndTheRequestIsMarkedNotSent() {
        FakeProvider provider = new FakeProvider(RetryFixtures.providerUrl(module, 31001)).availableAfter(Duration.ofHours(1));
        RpcInvocation invocation = RetryFixtures.invocation("read");

        assertThatThrownBy(() -> chain(provider).invoke(invocation))
                .isInstanceOfSatisfying(RpcException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo(RpcException.NETWORK_EXCEPTION);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getMessage()).contains("请求未发出");
                });
        assertThat(invocation.get(UnprocessedRequestFilter.NOT_SENT)).isEqualTo(Boolean.TRUE);
        assertThat(provider.calls()).isZero();
    }

    @Test
    void availableProviderIsInvokedOnceWithItsDeclarationRecorded() {
        FakeProvider provider = new FakeProvider(RetryFixtures.providerUrl(module, 31001, "read.idempotent", "true"));
        RpcInvocation invocation = RetryFixtures.invocation("read");

        chain(provider).invoke(invocation);

        assertThat(provider.calls()).isEqualTo(1);
        assertThat(invocation.get(UnprocessedRequestFilter.IDEMPOTENT)).isEqualTo(Boolean.TRUE);
        assertThat(invocation.get(UnprocessedRequestFilter.NOT_SENT)).isNull();
    }

    @Test
    void undeclaredMethodIsRecordedAsNotIdempotent() {
        FakeProvider provider = new FakeProvider(RetryFixtures.providerUrl(module, 31001, "read.idempotent", "true"));
        RpcInvocation invocation = RetryFixtures.invocation("write");

        chain(provider).invoke(invocation);

        assertThat(invocation.get(UnprocessedRequestFilter.IDEMPOTENT)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void asyncInvocationIsPassedThroughUntouched() {
        FakeProvider provider = new FakeProvider(RetryFixtures.providerUrl(module, 31001, "read.idempotent", "true"))
                .availableAfter(Duration.ofHours(1));
        RpcInvocation invocation = RetryFixtures.invocation("read");
        invocation.setInvokeMode(InvokeMode.ASYNC);

        chain(provider).invoke(invocation);

        assertThat(provider.calls()).isEqualTo(1);
        assertThat(invocation.get(UnprocessedRequestFilter.NOT_SENT)).isNull();
        assertThat(invocation.get(UnprocessedRequestFilter.IDEMPOTENT)).isNull();
    }

    @Test
    void filterIsInnermostAmongConsumerFilters() {
        URL url = RetryFixtures.providerUrl(module, 31001, "generic", "true", "actives", "1", "deprecated", "true");

        List<Filter> filters = module.getExtensionLoader(Filter.class)
                .getActivateExtension(url, CommonConstants.REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);

        assertThat(filters).extracting(filter -> filter.getClass().getSimpleName())
                .contains("GenericImplFilter", "ActiveLimitFilter", "DeprecatedFilter");
        assertThat(filters.get(filters.size() - 1)).isInstanceOf(UnprocessedRequestFilter.class);
    }

    /**
     * 锁定 Dubbo 3.3.7 的行为：经注册中心元数据传来的提供方方法参数，调用端写了同名参数时以调用端为准。
     */
    @Test
    void consumerParameterOverridesProviderDeclaration() {
        URL provider = URL.valueOf("tri://provider.test:31001/" + RetryFixtures.INTERFACE
                + "?interface=" + RetryFixtures.INTERFACE + "&side=provider&read.idempotent=true").setScopeModel(application);
        MetadataInfo metadata = new MetadataInfo("retry-test-provider");
        metadata.addService(provider);
        InstanceAddressURL address = new InstanceAddressURL(
                new DefaultServiceInstance("retry-test-provider", "provider.test", 31001, application), metadata, "tri");
        String consumer = "tri://consumer.test/" + RetryFixtures.INTERFACE + "?interface=" + RetryFixtures.INTERFACE + "&side=consumer";

        RpcContext.getServiceContext().setConsumerUrl(URL.valueOf(consumer + "&read.idempotent=false"));
        assertThat(address.getMethodParameter("read", DubboRetryPolicy.IDEMPOTENT_KEY, false)).isFalse();

        RpcContext.getServiceContext().setConsumerUrl(URL.valueOf(consumer));
        assertThat(address.getMethodParameter("read", DubboRetryPolicy.IDEMPOTENT_KEY, false)).isTrue();
    }

    private Invoker<Service> chain(FakeProvider provider) {
        return new DefaultFilterChainBuilder().buildInvokerChain(
                provider, CommonConstants.REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);
    }
}
