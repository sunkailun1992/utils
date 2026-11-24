package com.kellen.config.dubbo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * gRFC A6 的重试限流令牌桶，写法照 grpc-java 的 {@code RetriableStream.Throttle}：以千分之一个令牌为单位存放，用 CAS 更新。
 *
 * <p>每次失败扣 1 个令牌，每次成功加回 {@code tokenRatio} 个，不超过上限、不低于 0；令牌不高于上限的一半时不再重试。</p>
 *
 * @author 孙凯伦
 */
final class DubboRetryBudget {

    private static final int SCALE = 1000;

    private final int maxTokens;

    private final int threshold;

    private final int tokenRatio;

    private final AtomicInteger tokens;

    /**
     * 新建一个满额的令牌桶。
     *
     * @param maxTokens  初值与上限，个
     * @param tokenRatio 每次成功加回的令牌数
     */
    DubboRetryBudget(int maxTokens, double tokenRatio) {
        if (maxTokens <= 0 || tokenRatio <= 0) {
            throw new IllegalArgumentException("令牌上限与成功加回数都必须大于 0");
        }
        this.maxTokens = maxTokens * SCALE;
        this.threshold = this.maxTokens / 2;
        this.tokenRatio = (int) Math.round(tokenRatio * SCALE);
        this.tokens = new AtomicInteger(this.maxTokens);
    }

    /**
     * @return 令牌高于上限的一半时为真
     */
    boolean allowsRetry() {
        return tokens.get() > threshold;
    }

    /**
     * 记一次失败：扣 1 个令牌，不低于 0。
     */
    void recordFailure() {
        tokens.updateAndGet(current -> Math.max(current - SCALE, 0));
    }

    /**
     * 记一次成功：加回 {@code tokenRatio} 个令牌，不超过上限。
     */
    void recordSuccess() {
        tokens.updateAndGet(current -> Math.min(current + tokenRatio, maxTokens));
    }

    /**
     * @return 当前令牌数
     */
    double tokens() {
        return tokens.get() / (double) SCALE;
    }
}
