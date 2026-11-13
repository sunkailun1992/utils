package com.kellen.config.dubbo;

import com.kellen.traffic.CanaryGrant;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.time.Instant;

/**
 * 在发出请求前核对所选实例的静态标签，防止动态规则将灰度请求指向其他实例。
 */
@Activate(group = CommonConstants.CONSUMER, order = 0)
public class DubboCanaryProviderGuard implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (!(invocation.get(DubboCanaryClusterFilter.ROUTING_ATTEMPT)
                instanceof DubboCanaryClusterFilter.RoutingAttempt attempt)) {
            return invoker.invoke(invocation);
        }
        CanaryGrant grant = attempt.grant;
        if (grant != null) {
            if (!grant.expiresAt().isAfter(Instant.now())) {
                attempt.rejected = true;
                throw new RpcException("Canary grant expired before Dubbo transport");
            }
            if (!grant.tag().equals(invoker.getUrl().getParameter(CommonConstants.TAG_KEY))) {
                attempt.rejected = true;
                throw new RpcException("Selected Dubbo provider does not match the canary grant");
            }
        }
        attempt.record("selected");
        return invoker.invoke(invocation);
    }
}
