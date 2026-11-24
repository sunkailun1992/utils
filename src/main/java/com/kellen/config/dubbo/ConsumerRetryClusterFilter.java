package com.kellen.config.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.TimeoutCountDown;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.directory.AbstractDirectory;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.apache.dubbo.rpc.support.RpcUtils;
import org.springframework.util.backoff.BackOffExecution;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Dubbo 调用端集群层过滤器：同步调用的统一重试，全服务群只有这一层。
 *
 * <ul>
 *     <li>第一层（gRFC A6 的透明重试）：请求没发出的失败（协议层 {@link UnprocessedRequestFilter} 标了“未发出”，
 *     或有提供方正暂时被移出可用列表、等待目录复查），任何方法都重试，不计次数，不扣预算，直到这次调用的截止时间。</li>
 *     <li>第二层（A6 的重试策略）：请求发出后因连接断开或服务端暂不可用而失败，只有提供方声明了幂等的方法才重试，
 *     最多 {@value DubboRetryPolicy#MAX_SENT_ATTEMPTS} 次尝试，并受按（调用方应用，提供方服务）计的令牌桶预算约束。</li>
 *     <li>超时、业务异常与其它失败一律不重试（见 {@link DubboFailureClassifier}）。</li>
 * </ul>
 *
 * <p>每次尝试前把调用上下文附件的 {@code retries} 设为 0（{@code FailoverClusterInvoker#calculateInvokeTimes} 读后即删），
 * Dubbo 自己只试一次；同时放入这次调用的超时倒计时（{@code TimeoutCountDown}），下层按剩余时间设超时。上游请求带来了倒计时就沿用它，
 * 与 {@code RpcUtils#calculateTimeout} 的取值顺序一致。退避用 {@link DubboRetryPolicy#startBackOff()}，剩余时间不够一次退避就放弃，
 * 不睡到截止时间，与 Spring Framework 7 {@code RetryTemplate} 的超时判断一致。</p>
 *
 * <p>排在调用端集群过滤器链的最内层：consumercontext、灰度路由、指标、monitor、future 等过滤器把整个重试过程当成一次调用，
 * 与 Dubbo 自带失败转移在集群调用端内部循环的位置一致；每次重试都重新走目录、路由与负载均衡。异步调用原样下传。</p>
 *
 * @author 孙凯伦
 */
@Activate(group = CommonConstants.CONSUMER, order = 100_000)
public class ConsumerRetryClusterFilter implements ClusterFilter {

    private final ConcurrentMap<String, DubboRetryBudget> budgets = new ConcurrentHashMap<>();

    /**
     * 由 Dubbo 扩展加载器创建，每个应用模型一个实例，预算随实例走。
     */
    public ConsumerRetryClusterFilter() {
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        if (RpcUtils.getInvokeMode(url, invocation) != InvokeMode.SYNC) {
            return invoker.invoke(invocation);
        }
        TimeoutCountDown deadline = deadline(url, invocation);
        DubboRetryBudget budget = budgets.computeIfAbsent(url.getApplication() + "/" + url.getServiceKey(),
                key -> new DubboRetryBudget(DubboRetryPolicy.BUDGET_MAX_TOKENS, DubboRetryPolicy.BUDGET_TOKEN_RATIO));
        BackOffExecution backOff = DubboRetryPolicy.startBackOff();
        int sentFailures = 0;
        while (true) {
            invocation.getAttributes().remove(UnprocessedRequestFilter.NOT_SENT);
            invocation.getAttributes().remove(UnprocessedRequestFilter.IDEMPOTENT);
            RpcContextAttachment attachments = RpcContext.getClientAttachment();
            attachments.setObjectAttachment(CommonConstants.RETRIES_KEY, 0);
            attachments.setObjectAttachment(CommonConstants.TIME_COUNTDOWN_KEY, deadline);
            RpcException failure;
            try {
                // 业务异常随结果返回、不抛 RpcException，与成功一样原样交回，并计为一次成功。
                Result result = invoker.invoke(invocation);
                budget.recordSuccess();
                return result;
            } catch (RpcException thrown) {
                failure = thrown;
            }
            DubboFailureCategory category = DubboFailureClassifier.classify(failure,
                    Boolean.TRUE.equals(invocation.get(UnprocessedRequestFilter.NOT_SENT)), awaitingRecheck(invoker));
            if (category == DubboFailureCategory.SENT_TRANSIENT) {
                budget.recordFailure();
                sentFailures++;
                if (!Boolean.TRUE.equals(invocation.get(UnprocessedRequestFilter.IDEMPOTENT))
                        || sentFailures >= DubboRetryPolicy.MAX_SENT_ATTEMPTS || !budget.allowsRetry()) {
                    throw failure;
                }
            } else if (category != DubboFailureCategory.UNPROCESSED) {
                throw failure;
            }
            long wait = backOff.nextBackOff();
            if (wait == BackOffExecution.STOP || deadline.timeRemaining(TimeUnit.MILLISECONDS) <= wait) {
                throw failure;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw failure;
            }
        }
    }

    /**
     * 这次调用的截止倒计时：上游请求带来的倒计时（ConsumerContextFilter 已放进客户端附件）优先，没有就按方法超时新建。
     */
    private static TimeoutCountDown deadline(URL url, Invocation invocation) {
        RpcContextAttachment attachments = RpcContext.getClientAttachment();
        if (attachments.getObjectAttachment(CommonConstants.TIME_COUNTDOWN_KEY) instanceof TimeoutCountDown upstream) {
            return upstream;
        }
        long timeout = RpcUtils.getTimeout(url, RpcUtils.getMethodName(invocation), attachments, invocation,
                CommonConstants.DEFAULT_TIMEOUT);
        return TimeoutCountDown.newCountDown(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * @return 目录里有提供方正暂时被移出可用列表、等待复查时为真（{@code AbstractDirectory#addInvalidateInvoker}）
     */
    private static boolean awaitingRecheck(Invoker<?> invoker) {
        return invoker instanceof ClusterInvoker<?> cluster
                && cluster.getDirectory() instanceof AbstractDirectory<?> directory
                && !directory.getInvokersToReconnect().isEmpty();
    }
}
