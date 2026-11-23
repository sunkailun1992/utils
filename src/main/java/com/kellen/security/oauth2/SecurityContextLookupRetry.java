package com.kellen.security.oauth2;

import org.apache.dubbo.rpc.RpcException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.util.ClassUtils;

import java.time.Duration;

/**
 * 业务上下文查询的重试策略：只对请求还没到 user 服务的连接类失败，按指数退避加抖动重试
 * （gRFC A6 的按方法重试策略，用 Spring Framework 7 的 {@link RetryPolicy} 实现）。
 *
 * <p>为什么要重试：Dubbo 3.3.7 的 Triple 调用端在连接断开时直接失败、不等重连（{@code TripleInvoker#doInvoke} 报 UNAVAILABLE），
 * 连接断开 1 秒后才重连（{@code NettyConnectionHandler#reconnect}）；选路发现提供方不可用还会把它移出可用列表、每秒复查一次
 * （{@code AbstractDirectory}），这期间的请求得到“没有可用提供方”。gRPC 的做法是连接重连期间不让请求失败（wait-for-ready 语义），
 * 没离开客户端的请求透明重试（gRFC A6）。Dubbo 自带的失败转移（{@code retries}）立即重试、没有退避，只有一个提供方时盖不住
 * 这 1 至 2 秒，所以引用仍设 {@code retries = 0}，由这里退避重试。</p>
 *
 * <p>重试什么：异常链上的 Dubbo {@link RpcException} 是网络错误（Triple 的 UNAVAILABLE；同步调用时 Dubbo 把它包成未知错误，
 * 所以沿异常链找）或没有可用提供方。这两类请求都没到提供方，重试不会重复执行；业务上下文查询本身也是只读的
 * （RFC 9110 §9.2.2：幂等请求可以自动重试）。不重试：超时（单次已用掉 3 秒，再试只会放大延迟与负载）、业务异常、限流、
 * 序列化、鉴权等带明确错误码的失败，以及提供方应用自己抛出的异常。</p>
 *
 * <p>参数：最多重试 {@value #MAX_RETRIES} 次（共 5 次，即 A6 的次数上限）；首次等 250 毫秒、每次翻倍、最长 1 秒，
 * 抖动 50 毫秒且随倍数放大，即每次约 ±20%（同 A6）。累计等待 2.25 至 2.9 秒：盖住“断开 1 秒后重连、目录复查没赶上再等
 * 1 秒”的约 2 秒，又不超过原单次 3 秒的超时。</p>
 *
 * @author 孙凯伦
 */
final class SecurityContextLookupRetry {

    /**
     * 最多重试次数（不含第一次）。
     */
    static final long MAX_RETRIES = 4;

    /**
     * 第一次重试前的等待。
     */
    static final Duration INITIAL_DELAY = Duration.ofMillis(250);

    /**
     * 每次重试等待的倍数。
     */
    static final double MULTIPLIER = 2.0;

    /**
     * 单次等待的上限。
     */
    static final Duration MAX_DELAY = Duration.ofSeconds(1);

    /**
     * 第一次等待的抖动，之后随倍数放大。
     */
    static final Duration JITTER = Duration.ofMillis(50);

    /**
     * utils 以 {@code api} 依赖带上 Dubbo；消费者排除 Dubbo 时不加载 {@link DubboFailures}，也就不重试。
     */
    private static final boolean DUBBO_PRESENT =
            ClassUtils.isPresent("org.apache.dubbo.rpc.RpcException", SecurityContextLookupRetry.class.getClassLoader());

    private SecurityContextLookupRetry() {
    }

    /**
     * @return 默认重试策略
     */
    static RetryPolicy defaultPolicy() {
        return policy(INITIAL_DELAY, JITTER, MAX_DELAY);
    }

    /**
     * 按给定等待构造策略，重试次数与判断条件同默认策略。
     *
     * @param delay    第一次重试前的等待
     * @param jitter   第一次等待的抖动
     * @param maxDelay 单次等待的上限
     * @return 重试策略
     */
    static RetryPolicy policy(Duration delay, Duration jitter, Duration maxDelay) {
        return RetryPolicy.builder()
                .maxRetries(MAX_RETRIES)
                .delay(delay)
                .multiplier(MULTIPLIER)
                .jitter(jitter)
                .maxDelay(maxDelay)
                .predicate(SecurityContextLookupRetry::isConnectionFailure)
                .build();
    }

    /**
     * 失败是否属于请求还没到提供方的连接类失败。
     *
     * @param failure 一次查询的失败
     * @return 可以重试时为 {@code true}
     */
    static boolean isConnectionFailure(Throwable failure) {
        return DUBBO_PRESENT && DubboFailures.notReachedProvider(failure);
    }

    /**
     * 按 Dubbo 错误码判断；只在类路径上有 Dubbo 时加载。
     */
    private static final class DubboFailures {

        /**
         * 沿异常链查找的层数上限。
         */
        private static final int MAX_CAUSE_DEPTH = 8;

        private DubboFailures() {
        }

        private static boolean notReachedProvider(Throwable failure) {
            Throwable current = failure;
            for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
                if (current instanceof RpcException rpc) {
                    if (rpc.isNetwork() || rpc.isNoInvokerAvailableAfterFilter()) {
                        return true;
                    }
                    if (rpc.getCode() != RpcException.UNKNOWN_EXCEPTION) {
                        // 超时、业务、限流等已有明确错误码，不再往下找。
                        return false;
                    }
                }
                current = current.getCause();
            }
            return false;
        }
    }
}
