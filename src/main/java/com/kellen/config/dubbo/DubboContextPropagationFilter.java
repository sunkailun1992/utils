package com.kellen.config.dubbo;

import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Dubbo RPC 上下文透传过滤器。
 *
 * <p>负责在 Dubbo 调用链路中透传动态数据源、租户和 Seata XID，并在 Provider
 * 线程上临时绑定上下文后清理，避免线程复用串库、串租户或串事务。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -10000)
public class DubboContextPropagationFilter implements Filter {

    /**
     * 动态数据源 attachment key，与 HTTP 请求头保持一致。
     */
    private static final String DATA_SOURCE_KEY = "dataSource";

    /**
     * Seata 小写 XID key，兼容 Seata RPC 过滤器历史约定。
     */
    private static final String LOWER_XID_KEY = "tx_xid";

    /**
     * 租户配置属性。Dubbo SPI 未完成 Spring 注入时使用默认头名兜底。
     */
    private TenantProperties tenantProperties = new TenantProperties();

    /**
     * Dubbo SPI/Spring 扩展注入入口。
     *
     * @param tenantProperties 租户配置属性
     */
    public void setTenantProperties(TenantProperties tenantProperties) {
        if (tenantProperties != null) {
            this.tenantProperties = tenantProperties;
        }
    }

    /**
     * 执行 Dubbo 调用上下文透传。
     *
     * @param invoker    Dubbo 调用器
     * @param invocation Dubbo 调用信息
     * @return Dubbo 调用结果
     * @throws RpcException RPC 异常
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String side = invoker.getUrl().getParameter(CommonConstants.SIDE_KEY);
        if (isConsumerSide(side)) {
            attachConsumerContext(invocation);
            return invoker.invoke(invocation);
        }
        return invokeProvider(invoker, invocation);
    }

    /**
     * 判断当前调用是否为 Consumer 侧。
     *
     * @param side Dubbo URL side 参数
     * @return true 表示 Consumer 侧
     */
    private boolean isConsumerSide(String side) {
        if (CommonConstants.CONSUMER.equals(side)) {
            return true;
        }
        return RpcContext.getServiceContext().getUrl() != null && RpcContext.getServiceContext().isConsumerSide();
    }

    /**
     * Consumer 发起调用前写入上下文 attachment。
     *
     * @param invocation Dubbo 调用信息
     */
    private void attachConsumerContext(Invocation invocation) {
        String dataSource = DynamicSourceTtl.get();
        if (StringUtils.isNotBlank(dataSource)) {
            putAttachment(invocation, DATA_SOURCE_KEY, dataSource);
        }
        String currentXid = RootContext.getXID();
        if (StringUtils.isNotBlank(currentXid)) {
            putAttachment(invocation, RootContext.KEY_XID, currentXid);
            putAttachment(invocation, LOWER_XID_KEY, currentXid);
        }
        String tenantId = TenantContextHolder.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            putTenantAttachment(invocation, tenantId);
        }
        log.debug("Dubbo consumer context attached: method={}, dataSource={}, xidPresent={}, tenantPresent={}",
                invocation.getMethodName(), dataSource, StringUtils.isNotBlank(currentXid), StringUtils.isNotBlank(tenantId));
    }

    /**
     * Provider 执行期间临时绑定调用方上下文。
     *
     * @param invoker    Dubbo 调用器
     * @param invocation Dubbo 调用信息
     * @return Dubbo 调用结果
     */
    private Result invokeProvider(Invoker<?> invoker, Invocation invocation) {
        boolean dataSourceBound = false;
        boolean tenantBound = false;
        boolean xidBound = false;
        String previousTenantId = TenantContextHolder.getTenantId();
        boolean previousTenantIgnore = TenantContextHolder.isIgnore();
        try {
            String dataSource = firstAttachment(invocation, DATA_SOURCE_KEY);
            if (StringUtils.isNotBlank(dataSource)) {
                DynamicSourceTtl.push(dataSource);
                dataSourceBound = true;
            }
            String tenantId = tenantAttachment(invocation);
            if (StringUtils.isNotBlank(tenantId)) {
                TenantContextHolder.setTenantId(tenantId);
                tenantBound = true;
            }
            String xid = firstAttachment(invocation, RootContext.KEY_XID, LOWER_XID_KEY);
            String currentXid = RootContext.getXID();
            if (StringUtils.isNotBlank(xid) && StringUtils.isBlank(currentXid)) {
                RootContext.bind(xid);
                xidBound = true;
            } else if (StringUtils.isNotBlank(xid) && !StringUtils.equals(xid, currentXid)) {
                log.warn("Dubbo provider received different Seata XID, incoming={}, current={}", xid, currentXid);
            }
            log.debug("Dubbo provider context bound: method={}, dataSource={}, xidBound={}, tenantBound={}",
                    invocation.getMethodName(), dataSource, xidBound, tenantBound);
            return invoker.invoke(invocation);
        } finally {
            if (xidBound) {
                RootContext.unbind();
            }
            if (tenantBound) {
                restoreTenant(previousTenantId, previousTenantIgnore);
            }
            if (dataSourceBound) {
                DynamicSourceTtl.clear();
            }
        }
    }

    /**
     * 写入 invocation 与 client attachment，兼容不同 Dubbo 调用链读取位置。
     *
     * @param invocation Dubbo 调用信息
     * @param key        attachment key
     * @param value      attachment value
     */
    private void putAttachment(Invocation invocation, String key, String value) {
        invocation.setAttachment(key, value);
        RpcContext.getClientAttachment().setAttachment(key, value);
    }

    /**
     * 按租户头配置写入租户 attachment。
     *
     * @param invocation Dubbo 调用信息
     * @param tenantId   租户ID
     */
    private void putTenantAttachment(Invocation invocation, String tenantId) {
        List<String> headerNames = tenantProperties.getHeaderNames();
        if (headerNames == null || headerNames.isEmpty()) {
            putAttachment(invocation, "tenantId", tenantId);
            return;
        }
        for (String headerName : headerNames) {
            if (StringUtils.isNotBlank(headerName)) {
                putAttachment(invocation, headerName, tenantId);
            }
        }
    }

    /**
     * 读取第一个非空 attachment。
     *
     * @param invocation Dubbo 调用信息
     * @param keys       attachment keys
     * @return attachment 值
     */
    private String firstAttachment(Invocation invocation, String... keys) {
        RpcContextAttachment serverAttachment = RpcContext.getServerAttachment();
        for (String key : keys) {
            String value = invocation.getAttachment(key);
            if (StringUtils.isBlank(value)) {
                value = serverAttachment.getAttachment(key);
            }
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 读取租户 attachment。
     *
     * @param invocation Dubbo 调用信息
     * @return 租户ID
     */
    private String tenantAttachment(Invocation invocation) {
        List<String> headerNames = tenantProperties.getHeaderNames();
        if (headerNames == null || headerNames.isEmpty()) {
            return firstAttachment(invocation, "tenantId", "tenant-id", "X-Tenant-Id");
        }
        for (String headerName : headerNames) {
            String tenantId = firstAttachment(invocation, headerName);
            if (StringUtils.isNotBlank(tenantId)) {
                return tenantId;
            }
        }
        return null;
    }

    /**
     * 恢复 Provider 线程原有租户上下文。
     *
     * @param previousTenantId     调用前租户ID
     * @param previousTenantIgnore 调用前忽略租户标记
     */
    private void restoreTenant(String previousTenantId, boolean previousTenantIgnore) {
        TenantContextHolder.clear();
        if (StringUtils.isNotBlank(previousTenantId)) {
            TenantContextHolder.setTenantId(previousTenantId);
        }
        if (previousTenantIgnore) {
            TenantContextHolder.ignore();
        }
    }
}
