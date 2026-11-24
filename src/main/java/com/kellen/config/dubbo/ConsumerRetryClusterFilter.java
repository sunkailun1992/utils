package com.kellen.config.dubbo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.StatusRpcException;
import org.apache.dubbo.rpc.TimeoutCountDown;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.directory.AbstractDirectory;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.apache.dubbo.rpc.support.RpcUtils;
import org.springframework.util.backoff.BackOffExecution;

import java.util.Objects;
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
 * <p>可观测：重试、重试后成功与放弃各计一个计数器（仿 Envoy 的 {@code upstream_rq_retry}、{@code _success}、
 * {@code _overflow}、{@code _limit_exceeded}），注册在 Micrometer 全局注册表上；重试后成功记 INFO，放弃记 WARN，每次重试记 DEBUG。
 * 失败原因只写异常类名、Dubbo 错误码与 Triple 状态码，不记参数、返回值、附件与异常消息。第一次看到某个方法的幂等声明、
 * 或声明变了，记一行 INFO，便于运行时确认提供方的声明已经传到调用端。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
@Activate(group = CommonConstants.CONSUMER, order = 100_000)
public class ConsumerRetryClusterFilter implements ClusterFilter {

    /**
     * 重试次数，标签 service、method、layer（{@code unprocessed} 或 {@code sent}）。
     */
    static final String RETRIES_METRIC = "dubbo.consumer.retries";

    /**
     * 重试后成功的调用数，标签 service、method。
     */
    static final String RECOVERIES_METRIC = "dubbo.consumer.retry.recoveries";

    /**
     * 放弃重试的调用数，标签 service、method、reason。
     */
    static final String GIVE_UPS_METRIC = "dubbo.consumer.retry.giveups";

    private static final int MAX_DESCRIBED_CAUSES = 8;

    private final ConcurrentMap<String, DubboRetryBudget> budgets = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Boolean> declarations = new ConcurrentHashMap<>();

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
        Call call = new Call(url.getServiceInterface(), RpcUtils.getMethodName(invocation), System.nanoTime());
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
            call.attempts++;
            RpcException failure;
            try {
                // 业务异常随结果返回、不抛 RpcException，与成功一样原样交回，并计为一次成功。
                Result result = invoker.invoke(invocation);
                noteDeclaration(call, invocation);
                budget.recordSuccess();
                if (call.attempts > 1) {
                    recovered(call);
                }
                return result;
            } catch (RpcException thrown) {
                failure = thrown;
            }
            noteDeclaration(call, invocation);
            DubboFailureCategory category = DubboFailureClassifier.classify(failure,
                    Boolean.TRUE.equals(invocation.get(UnprocessedRequestFilter.NOT_SENT)), awaitingRecheck(invoker));
            call.noteFailure(category, failure);
            String giveUpReason = null;
            if (category == DubboFailureCategory.SENT_TRANSIENT) {
                budget.recordFailure();
                sentFailures++;
                if (!Boolean.TRUE.equals(invocation.get(UnprocessedRequestFilter.IDEMPOTENT))) {
                    giveUpReason = "not_idempotent";
                } else if (sentFailures >= DubboRetryPolicy.MAX_SENT_ATTEMPTS) {
                    giveUpReason = "attempts";
                } else if (!budget.allowsRetry()) {
                    giveUpReason = "budget";
                }
            } else if (category != DubboFailureCategory.UNPROCESSED) {
                if (call.attempts > 1) {
                    gaveUp(call, "not_retryable");
                }
                throw failure;
            }
            if (giveUpReason == null) {
                long wait = backOff.nextBackOff();
                if (wait == BackOffExecution.STOP || deadline.timeRemaining(TimeUnit.MILLISECONDS) <= wait) {
                    giveUpReason = "deadline";
                } else {
                    log.debug("Dubbo 调用 {}#{} 第 {} 次尝试失败：{}（{}），{} 毫秒后重试",
                            call.service, call.method, call.attempts, category, describe(failure), wait);
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        giveUpReason = "interrupted";
                    }
                }
            }
            if (giveUpReason != null) {
                gaveUp(call, giveUpReason);
                throw failure;
            }
            count(RETRIES_METRIC, call, "layer", category == DubboFailureCategory.UNPROCESSED ? "unprocessed" : "sent");
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

    /**
     * 第一次看到某个方法的幂等声明，或声明变了，记一行 INFO。
     */
    private void noteDeclaration(Call call, Invocation invocation) {
        if (!(invocation.get(UnprocessedRequestFilter.IDEMPOTENT) instanceof Boolean declared)) {
            return;
        }
        Boolean previous = declarations.put(call.service + "#" + call.method, declared);
        if (!Objects.equals(previous, declared)) {
            log.info("Dubbo 调用端 {}#{} 的幂等声明：{}", call.service, call.method, declared);
        }
    }

    private static void recovered(Call call) {
        log.info("Dubbo 调用 {}#{} 重试 {} 次后成功，用时 {} 毫秒；第一次失败：{}（{}）", call.service, call.method,
                call.attempts - 1, call.elapsedMillis(), call.firstCategory, describe(call.firstFailure));
        count(RECOVERIES_METRIC, call, null, null);
    }

    private static void gaveUp(Call call, String reason) {
        log.warn("Dubbo 调用 {}#{} 放弃重试（{}），共尝试 {} 次，用时 {} 毫秒；第一次失败：{}（{}）", call.service, call.method,
                reason, call.attempts, call.elapsedMillis(), call.firstCategory, describe(call.firstFailure));
        count(GIVE_UPS_METRIC, call, "reason", reason);
    }

    /**
     * 计数；指标出错不影响调用本身。
     */
    private static void count(String metric, Call call, String tagKey, String tagValue) {
        try {
            Counter.Builder counter = Counter.builder(metric).tag("service", call.service).tag("method", call.method);
            if (tagKey != null) {
                counter.tag(tagKey, tagValue);
            }
            counter.register(Metrics.globalRegistry).increment();
        } catch (RuntimeException ignored) {
            // 调用结果不能依赖遥测。
        }
    }

    /**
     * @return 失败的摘要：异常类名、Dubbo 错误码，原因链上有 Triple 状态或 Dubbo 远程异常时一并写出；不含异常消息
     */
    private static String describe(RpcException failure) {
        if (failure == null) {
            return "无";
        }
        StringBuilder summary = new StringBuilder(failure.getClass().getSimpleName())
                .append(" 错误码 ").append(failure.getCode());
        Throwable current = failure.getCause();
        for (int depth = 0; current != null && depth < MAX_DESCRIBED_CAUSES; depth++) {
            if (current instanceof StatusRpcException status) {
                return summary.append("，Triple ").append(status.getStatus().code).toString();
            }
            if (current instanceof RemotingException) {
                return summary.append("，").append(current.getClass().getSimpleName()).toString();
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return summary.toString();
    }

    /**
     * 一次调用的重试过程。
     */
    private static final class Call {

        private final String service;

        private final String method;

        private final long startNanos;

        private int attempts;

        private DubboFailureCategory firstCategory;

        private RpcException firstFailure;

        private Call(String service, String method, long startNanos) {
            this.service = service;
            this.method = method;
            this.startNanos = startNanos;
        }

        private void noteFailure(DubboFailureCategory category, RpcException failure) {
            if (firstFailure == null) {
                firstCategory = category;
                firstFailure = failure;
            }
        }

        private long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        }
    }
}
