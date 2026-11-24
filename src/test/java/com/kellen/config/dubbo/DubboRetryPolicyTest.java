package com.kellen.config.dubbo;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退避间隔：首次 100 毫秒、每次翻倍、最长 1 秒，抖动为当前间隔的 ±20%，结果夹在 [100, 1000] 毫秒内。
 */
class DubboRetryPolicyTest {

    private static final int SAMPLES = 200;

    @Test
    void firstDelayIsBetween100And120() {
        for (int sample = 0; sample < SAMPLES; sample++) {
            assertThat(DubboRetryPolicy.startBackOff().nextBackOff()).isBetween(100L, 120L);
        }
    }

    @Test
    void delaysDoubleWithin20Percent() {
        for (int sample = 0; sample < SAMPLES; sample++) {
            BackOffExecution backOff = DubboRetryPolicy.startBackOff();
            backOff.nextBackOff();
            assertThat(backOff.nextBackOff()).isBetween(160L, 240L);
            assertThat(backOff.nextBackOff()).isBetween(320L, 480L);
            assertThat(backOff.nextBackOff()).isBetween(640L, 960L);
        }
    }

    @Test
    void delayStaysWithinTheCap() {
        for (int sample = 0; sample < SAMPLES; sample++) {
            BackOffExecution backOff = DubboRetryPolicy.startBackOff();
            for (int attempt = 1; attempt <= 4; attempt++) {
                backOff.nextBackOff();
            }
            for (int attempt = 5; attempt <= 10; attempt++) {
                assertThat(backOff.nextBackOff()).as("第 %d 次", attempt).isBetween(800L, 1000L);
            }
        }
    }
}
