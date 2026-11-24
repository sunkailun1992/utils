package com.kellen.config.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.support.RpcUtils;

/**
 * Dubbo 调用端协议层过滤器：同步调用发请求前，先问所选提供方的连接是否可用；不可用就不发，标记“未发出”。
 *
 * <p>Dubbo 3.3.7 的 Triple 调用端在连接没连上时直接失败、不等重连（{@code TripleInvoker#doInvoke}）。在这里先判一次，
 * 集群层的 {@link ConsumerRetryClusterFilter} 就能确定这次请求没到服务端，任何方法都可以安全重试（gRFC A6 的透明重试）。
 * 同时记下本次所选提供方对该方法的幂等声明：提供方在 {@code @DubboService} 的方法参数里声明，经注册中心元数据传来，
 * 调用端的同名参数优先。</p>
 *
 * <p>“未发出”与幂等声明都写在调用对象的本地属性里（只在本进程，不随请求发出），不用异常子类表达：失败转移
 * （{@code FailoverClusterInvoker}）会重新包装异常，只保留错误码与原因链。</p>
 *
 * <p>排在调用端过滤器链的最内层（内置过滤器中最大的是 genericimpl 的 20000），紧挨协议调用端。异步调用原样下传。</p>
 *
 * @author 孙凯伦
 */
@Activate(group = CommonConstants.CONSUMER, order = 100_000)
public class UnprocessedRequestFilter implements Filter {

    /**
     * 本地属性键：这次尝试的请求没有发出，值为 {@link Boolean#TRUE}。
     */
    static final Object NOT_SENT = new Object();

    /**
     * 本地属性键：本次所选提供方对该方法的幂等声明，值为 {@link Boolean}。
     */
    static final Object IDEMPOTENT = new Object();

    /**
     * 由 Dubbo 扩展加载器创建。
     */
    public UnprocessedRequestFilter() {
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 协议层的调用模式要到 AbstractInvoker#prepareInvocation 才写入；没写入时这里按 URL 与返回类型推断。
        if (RpcUtils.getInvokeMode(invoker.getUrl(), invocation) != InvokeMode.SYNC) {
            return invoker.invoke(invocation);
        }
        invocation.put(IDEMPOTENT, invoker.getUrl().getMethodParameter(
                RpcUtils.getMethodName(invocation), DubboRetryPolicy.IDEMPOTENT_KEY, false));
        if (!invoker.isAvailable()) {
            invocation.put(NOT_SENT, Boolean.TRUE);
            throw new RpcException(RpcException.NETWORK_EXCEPTION,
                    "请求未发出：提供方 " + invoker.getUrl().getAddress() + " 的连接不可用");
        }
        return invoker.invoke(invocation);
    }
}
