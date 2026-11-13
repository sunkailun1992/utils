package com.kellen.config.dubbo;

import com.kellen.observability.fleet.TrafficRouteObservation;
import com.kellen.traffic.CanaryContext;
import com.kellen.traffic.CanaryGrant;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在 Dubbo 复制上下文附件之后、选择服务实例之前应用已验证的灰度授权。
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class DubboCanaryClusterFilter implements ClusterFilter {

    private static final String CANARY_ENABLED = "traffic.canary.enabled";
    static final Object ROUTING_ATTEMPT = new Object();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // Dubbo 可能从入站 RPC 自动复制该附件。仅显式启用的引用可转发它。
        invocation.getObjectAttachments().remove(CanaryGrant.ATTACHMENT);
        if (!invoker.getUrl().getParameter(CANARY_ENABLED, false)) {
            return invoker.invoke(invocation);
        }
        CanaryContext.Snapshot canary = CanaryContext.get();
        CanaryGrant grant = canary == null ? null : canary.grant();
        RoutingAttempt attempt = new RoutingAttempt(grant);
        invocation.put(ROUTING_ATTEMPT, attempt);
        if (canary != null) {
            if (!grant.expiresAt().isAfter(Instant.now())) {
                attempt.record("rejected");
                throw new RpcException("Canary grant expired before Dubbo routing");
            }
            invocation.setAttachment(CommonConstants.TAG_KEY, grant.tag());
            invocation.setAttachment(Constants.FORCE_USE_TAG, "true");
            invocation.setAttachment(CanaryGrant.ATTACHMENT, canary.token());
        }
        try {
            return invoker.invoke(invocation);
        } catch (RuntimeException failure) {
            attempt.recordFailure(failure);
            throw failure;
        }
    }

    static final class RoutingAttempt {

        final CanaryGrant grant;
        private final AtomicBoolean recorded = new AtomicBoolean();
        volatile boolean rejected;

        RoutingAttempt(CanaryGrant grant) {
            this.grant = grant;
        }

        void recordFailure(RuntimeException failure) {
            if (rejected) {
                record("rejected");
            } else if (failure instanceof RpcException rpcFailure && rpcFailure.isNoInvokerAvailableAfterFilter()) {
                record("unavailable");
            } else {
                record("failed");
            }
        }

        void record(String decision) {
            if (recorded.compareAndSet(false, true)) {
                TrafficRouteObservation.record("dubbo_consumer", grant == null ? "stable" : "canary", decision);
            }
        }
    }
}
