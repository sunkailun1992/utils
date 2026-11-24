package com.kellen.config.dubbo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * gRFC A6 的重试令牌桶：初值与上限 10，失败扣 1，成功加 0.1，令牌不高于一半时不再重试。
 */
class DubboRetryBudgetTest {

    private final DubboRetryBudget budget = new DubboRetryBudget(10, 0.1);

    @Test
    void startsFullAndAllowsRetry() {
        assertThat(budget.tokens()).isEqualTo(10.0);
        assertThat(budget.allowsRetry()).isTrue();
    }

    @Test
    void fiveFailuresReachTheThresholdAndStopRetries() {
        for (int failure = 0; failure < 4; failure++) {
            budget.recordFailure();
        }
        assertThat(budget.allowsRetry()).isTrue();

        budget.recordFailure();

        assertThat(budget.allowsRetry()).isFalse();
        assertThat(budget.tokens()).isEqualTo(5.0);
    }

    @Test
    void successAddsATenthUpToTheCap() {
        budget.recordFailure();
        budget.recordSuccess();
        assertThat(budget.tokens()).isEqualTo(9.1, within(1e-9));

        for (int success = 0; success < 20; success++) {
            budget.recordSuccess();
        }
        assertThat(budget.tokens()).isEqualTo(10.0);
    }

    @Test
    void tokensNeverGoBelowZero() {
        for (int failure = 0; failure < 15; failure++) {
            budget.recordFailure();
        }
        assertThat(budget.tokens()).isZero();
        assertThat(budget.allowsRetry()).isFalse();

        budget.recordSuccess();
        assertThat(budget.tokens()).isEqualTo(0.1, within(1e-9));
    }

    @Test
    void concurrentUpdatesStayWithinBounds() throws Exception {
        runConcurrently(16, thread -> {
            Random random = new Random(thread);
            for (int update = 0; update < 1000; update++) {
                if (random.nextBoolean()) {
                    budget.recordFailure();
                } else {
                    budget.recordSuccess();
                }
                assertThat(budget.tokens()).isBetween(0.0, 10.0);
            }
        });
        assertThat(budget.tokens()).isBetween(0.0, 10.0);
    }

    /**
     * 不触到上下限时，并发扣减一次都不能丢：16 个线程各扣 50 次，1000 个令牌应正好剩 200 个。
     */
    @Test
    void concurrentFailuresAreNeverLost() throws Exception {
        DubboRetryBudget large = new DubboRetryBudget(1000, 0.1);

        runConcurrently(16, thread -> {
            for (int failure = 0; failure < 50; failure++) {
                large.recordFailure();
            }
        });

        assertThat(large.tokens()).isEqualTo(200.0);
    }

    private static void runConcurrently(int threads, ThreadBody body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> running = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int index = thread;
                running.add(pool.submit(() -> {
                    start.await();
                    body.run(index);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : running) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThreadBody {
        void run(int thread);
    }
}
