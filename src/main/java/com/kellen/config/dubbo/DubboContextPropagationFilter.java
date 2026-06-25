package com.kellen.config.dubbo;

import com.kellen.security.config.TenantProperties;
import com.kellen.traffic.TrafficGovernanceContext;
import com.kellen.traffic.TrafficGovernanceHeaders;
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
 * <p>负责在 Dubbo 调用链路中透传动态数据源、租户、Seata XID 和流量治理上下文，
 * 并在 Provider 线程上临时绑定上下文后清理，避免线程复用串库、串租户、串事务或串泳道。</p>
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
        attachTrafficGovernanceContext(invocation);
        log.debug("Dubbo consumer context attached: method={}, dataSource={}, xidPresent={}, tenantPresent={}, trafficPresent={}",
                invocation.getMethodName(), dataSource, StringUtils.isNotBlank(currentXid), StringUtils.isNotBlank(tenantId),
                TrafficGovernanceContext.get() != null);
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
        boolean trafficBound = false;
        String previousTenantId = TenantContextHolder.getTenantId();
        boolean previousTenantIgnore = TenantContextHolder.isIgnore();
        TrafficGovernanceContext.Snapshot previousTraffic = TrafficGovernanceContext.get();
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
            trafficBound = bindTrafficGovernanceContext(invocation);
            log.debug("Dubbo provider context bound: method={}, dataSource={}, xidBound={}, tenantBound={}, trafficBound={}",
                    invocation.getMethodName(), dataSource, xidBound, tenantBound, trafficBound);
            return invoker.invoke(invocation);
        } finally {
            if (xidBound) {
                RootContext.unbind();
            }
            if (trafficBound) {
                restoreTraffic(previousTraffic);
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
     * 写入流量治理上下文 attachment。
     *
     * @param invocation Dubbo 调用信息
     */
    private void attachTrafficGovernanceContext(Invocation invocation) {
        TrafficGovernanceContext.Snapshot snapshot = TrafficGovernanceContext.get();
        if (snapshot == null) {
            return;
        }
        if (StringUtils.isNotBlank(snapshot.releaseVersion())) {
            putAttachment(invocation, TrafficGovernanceHeaders.RELEASE_VERSION, snapshot.releaseVersion());
            putAttachment(invocation, TrafficGovernanceHeaders.RELEASE_VERSION_ATTACHMENT, snapshot.releaseVersion());
        }
        if (StringUtils.isNotBlank(snapshot.lane())) {
            putAttachment(invocation, TrafficGovernanceHeaders.TRAFFIC_LANE, snapshot.lane());
            putAttachment(invocation, TrafficGovernanceHeaders.TRAFFIC_LANE_ATTACHMENT, snapshot.lane());
        }
        if (StringUtils.isNotBlank(snapshot.canaryTag())) {
            putAttachment(invocation, TrafficGovernanceHeaders.CANARY_TAG, snapshot.canaryTag());
            putAttachment(invocation, CommonConstants.TAG_KEY, snapshot.canaryTag());
            putAttachment(invocation, CommonConstants.DUBBO_TAG_HEADER, snapshot.canaryTag());
        }
        if (snapshot.canaryWeight() != null) {
            String weight = String.valueOf(snapshot.canaryWeight());
            putAttachment(invocation, TrafficGovernanceHeaders.CANARY_WEIGHT, weight);
            putAttachment(invocation, TrafficGovernanceHeaders.CANARY_WEIGHT_ATTACHMENT, weight);
        }
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
     * Provider 侧绑定流量治理上下文。
     *
     * @param invocation Dubbo 调用信息
     * @return true 表示绑定过新上下文
     */
    private boolean bindTrafficGovernanceContext(Invocation invocation) {
        String releaseVersion = firstAttachment(invocation,
                TrafficGovernanceHeaders.RELEASE_VERSION,
                TrafficGovernanceHeaders.RELEASE_VERSION_ATTACHMENT);
        String lane = firstAttachment(invocation,
                TrafficGovernanceHeaders.TRAFFIC_LANE,
                TrafficGovernanceHeaders.TRAFFIC_LANE_ATTACHMENT);
        String canaryTag = firstAttachment(invocation,
                TrafficGovernanceHeaders.CANARY_TAG,
                CommonConstants.TAG_KEY,
                CommonConstants.DUBBO_TAG_HEADER);
        Integer canaryWeight = parseWeight(firstAttachment(invocation,
                TrafficGovernanceHeaders.CANARY_WEIGHT,
                TrafficGovernanceHeaders.CANARY_WEIGHT_ATTACHMENT));
        TrafficGovernanceContext.Snapshot snapshot = new TrafficGovernanceContext.Snapshot(
                releaseVersion, lane, canaryTag, canaryWeight);
        if (snapshot.isEmpty()) {
            return false;
        }
        TrafficGovernanceContext.set(snapshot);
        return true;
    }

    /**
     * 解析受控权重。
     *
     * @param raw 原始权重
     * @return 0 到 100 之间的权重，非法时返回 null
     */
    private Integer parseWeight(String raw) {
        if (StringUtils.isBlank(raw) || !StringUtils.isNumeric(raw)) {
            return null;
        }
        int weight = Integer.parseInt(raw);
        return weight >= 0 && weight <= 100 ? weight : null;
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

    /**
     * 恢复 Provider 线程原有流量治理上下文。
     *
     * @param previousTraffic 调用前流量治理上下文
     */
    private void restoreTraffic(TrafficGovernanceContext.Snapshot previousTraffic) {
        TrafficGovernanceContext.clear();
        if (previousTraffic != null && !previousTraffic.isEmpty()) {
            TrafficGovernanceContext.set(previousTraffic);
        }
    }
}
