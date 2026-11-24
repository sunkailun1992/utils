package com.kellen.config.dubbo;

import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Dubbo 调用端统一重试的固定参数，取值依据 gRFC A6 的重试策略与重试限流。
 *
 * @author 孙凯伦
 */
final class DubboRetryPolicy {

    /**
     * 声明方法幂等用的方法参数名，提供方与调用端都写在 {@code @Method(name = "…", parameters = {"idempotent", "true"})} 里，
     * 调用端的同名参数优先。
     */
    static final String IDEMPOTENT_KEY = "idempotent";

    /**
     * 第二层（请求发出后的连接类失败）最多尝试的次数，含第一次；A6 的上限是 5。
     */
    static final int MAX_SENT_ATTEMPTS = 3;

    /**
     * 重试令牌桶的初值与上限。
     */
    static final int BUDGET_MAX_TOKENS = 10;

    /**
     * 每次成功加回的令牌数。
     */
    static final double BUDGET_TOKEN_RATIO = 0.1;

    /**
     * 首次退避，毫秒。
     */
    static final long INITIAL_BACKOFF_MILLIS = 100;

    /**
     * 退避倍数。
     */
    static final double BACKOFF_MULTIPLIER = 2.0;

    /**
     * 退避上限，毫秒。
     */
    static final long MAX_BACKOFF_MILLIS = 1000;

    /**
     * 首次退避的抖动，毫秒。Spring 按倍数放大它，即每次间隔的 ±20%，结果夹在 [首次退避, 退避上限] 内。
     */
    static final long BACKOFF_JITTER_MILLIS = 20;

    private DubboRetryPolicy() {
    }

    /**
     * 为一次调用开一个新的退避序列。不限次数与总时长：何时停由调用的截止时间、第二层次数与预算决定。
     *
     * @return 退避序列
     */
    static BackOffExecution startBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MILLIS, BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MILLIS);
        backOff.setJitter(BACKOFF_JITTER_MILLIS);
        return backOff.start();
    }
}
