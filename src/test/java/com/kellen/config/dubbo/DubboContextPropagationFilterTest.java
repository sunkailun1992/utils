package com.kellen.config.dubbo;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import io.seata.core.context.RootContext;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DubboContextPropagationFilterTest {

    private final DubboContextPropagationFilter filter = new DubboContextPropagationFilter();

    @AfterEach
    void tearDown() {
        if (RootContext.getXID() != null) {
            RootContext.unbind();
        }
        DynamicSourceTtl.clear();
        TenantContextHolder.clear();
    }

    @Test
    void consumerAttachesDatasourceTenantAndSeataXid() {
        DynamicSourceTtl.push(DynamicSourceTtl.SLAVE_DATASOURCE);
        TenantContextHolder.setTenantId("1001");
        RootContext.bind("xid-consumer");
        RpcInvocation invocation = invocation();

        filter.invoke(new TestInvoker(CommonConstants.CONSUMER, ignored -> new AppResponse("ok")), invocation);

        assertThat(invocation.getAttachment("dataSource")).isEqualTo(DynamicSourceTtl.SLAVE_DATASOURCE);
        assertThat(invocation.getAttachment("tenantId")).isEqualTo("1001");
        assertThat(invocation.getAttachment("tenant-id")).isEqualTo("1001");
        assertThat(invocation.getAttachment("X-Tenant-Id")).isEqualTo("1001");
        assertThat(invocation.getAttachment(RootContext.KEY_XID)).isEqualTo("xid-consumer");
        assertThat(invocation.getAttachment("tx_xid")).isEqualTo("xid-consumer");
    }

    @Test
    void providerBindsAndCleansDatasourceTenantAndSeataXid() {
        RpcInvocation invocation = invocation();
        invocation.setAttachment("dataSource", DynamicSourceTtl.SLAVE_DATASOURCE);
        invocation.setAttachment("tenantId", "1001");
        invocation.setAttachment(RootContext.KEY_XID, "xid-provider");
        AtomicReference<String> dataSourceInProvider = new AtomicReference<>();
        AtomicReference<String> dynamicHolderInProvider = new AtomicReference<>();
        AtomicReference<String> tenantInProvider = new AtomicReference<>();
        AtomicReference<String> xidInProvider = new AtomicReference<>();

        filter.invoke(new TestInvoker(CommonConstants.PROVIDER, ignored -> {
            dataSourceInProvider.set(DynamicSourceTtl.get());
            dynamicHolderInProvider.set(DynamicDataSourceContextHolder.peek());
            tenantInProvider.set(TenantContextHolder.getTenantId());
            xidInProvider.set(RootContext.getXID());
            return new AppResponse("ok");
        }), invocation);

        assertThat(dataSourceInProvider).hasValue(DynamicSourceTtl.SLAVE_DATASOURCE);
        assertThat(dynamicHolderInProvider).hasValue(DynamicSourceTtl.SLAVE_DATASOURCE);
        assertThat(tenantInProvider).hasValue("1001");
        assertThat(xidInProvider).hasValue("xid-provider");
        assertThat(RootContext.getXID()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(DynamicSourceTtl.dataSourceContext.get()).isNull();
        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    private RpcInvocation invocation() {
        return new RpcInvocation("test", "TestService", "", new Class<?>[0], new Object[0]);
    }

    private static class TestInvoker implements Invoker<Object> {

        private final URL url;

        private final Function<Invocation, Result> handler;

        private TestInvoker(String side, Function<Invocation, Result> handler) {
            this.url = URL.valueOf("tri://127.0.0.1:20880/com.kellen.TestService")
                    .addParameter(CommonConstants.SIDE_KEY, side);
            this.handler = handler;
        }

        @Override
        public Class<Object> getInterface() {
            return Object.class;
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            return handler.apply(invocation);
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() {
        }
    }
}
