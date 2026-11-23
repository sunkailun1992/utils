package com.kellen.security.oauth2;

import com.kellen.security.SecurityUser;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.TriRpcStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业务上下文查询的重试：只重试请求还没到 user 服务的连接类失败，其余失败第一次就按 user 不可用（503）处理。
 * 等待改成 1 毫秒，结论只由失败序列决定；默认策略的等待另由 {@link #defaultPolicyWaitsForReconnectAndRecheckWithinTheOriginalTimeout()} 核对。
 */
class FleetJwtAuthenticationConverterRetryTest {

    private static final RetryPolicy FAST = SecurityContextLookupRetry.policy(Duration.ofMillis(1), Duration.ZERO, Duration.ofMillis(1));

    private static final Jwt JWT = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-1").claim("user_id", "u1").build();

    private static final SecurityContextResult OK = SecurityContextResult.ok(SecurityUser.builder().userId("u1").username("u1-name")
            .tenantId("t1").deptId("d1").dataScope("SELF").dataScopeDeptIds(List.of()).authorities(List.of()).enabled(true)
            .tenantIds(List.of("t1")).idpSubject("kc-1").build());

    @Test
    void retriesWhileTheTripleConnectionIsNotReadyAndThenAuthenticates() {
        ScriptedProvider provider = new ScriptedProvider(unavailable(), unavailable());

        assertThat(statusOf(converter(provider).convert(JWT))).isEqualTo(SecurityContextResult.Status.OK);
        assertThat(provider.calls()).isEqualTo(3);
    }

    @Test
    void retriesWhileNoProviderIsAvailableAndThenAuthenticates() {
        ScriptedProvider provider = new ScriptedProvider(noProvider());

        assertThat(statusOf(converter(provider).convert(JWT))).isEqualTo(SecurityContextResult.Status.OK);
        assertThat(provider.calls()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("failuresThatMayHaveReachedTheProvider")
    void failuresThatMayHaveReachedTheProviderAreNotRetried(RuntimeException failure) {
        ScriptedProvider provider = new ScriptedProvider(failure);

        assertThatThrownBy(() -> converter(provider).convert(JWT))
                .isInstanceOf(FleetSecurityContextUnavailableException.class)
                .hasMessageStartingWith("加载用户安全上下文失败：")
                .hasCause(failure);
        assertThat(provider.calls()).isEqualTo(1);
    }

    static Stream<RuntimeException> failuresThatMayHaveReachedTheProvider() {
        return Stream.of(
                new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout"),
                new RpcException(RpcException.BIZ_EXCEPTION, "provider rejected"),
                new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION, "too many requests"),
                new RpcException(RpcException.UNKNOWN_EXCEPTION, "Fail to invoke remote method", new IllegalStateException("db down")),
                new RpcException(RpcException.TIMEOUT_EXCEPTION, "timeout wrapping a network failure", unavailable()),
                new IllegalStateException("provider application failure"));
    }

    @Test
    void givesUpAfterFourRetriesAndKeepsTheLastFailure() {
        RuntimeException[] failures = {unavailable(), noProvider(), unavailable(), noProvider(), unavailable()};
        ScriptedProvider provider = new ScriptedProvider(failures);

        assertThatThrownBy(() -> converter(provider).convert(JWT))
                .isInstanceOf(FleetSecurityContextUnavailableException.class)
                .hasMessageStartingWith("加载用户安全上下文失败（共尝试 5 次）：")
                .hasCause(failures[4]);
        assertThat(provider.calls()).isEqualTo(5);
    }

    @Test
    void defaultPolicyWaitsForReconnectAndRecheckWithinTheOriginalTimeout() {
        for (int sample = 0; sample < 200; sample++) {
            BackOffExecution backOff = SecurityContextLookupRetry.defaultPolicy().getBackOff().start();
            long total = 0;
            for (int retry = 0; retry < SecurityContextLookupRetry.MAX_RETRIES; retry++) {
                long delay = backOff.nextBackOff();
                assertThat(delay).isBetween(250L, 1000L);
                total += delay;
            }
            assertThat(backOff.nextBackOff()).isEqualTo(BackOffExecution.STOP);
            // Dubbo 断开 1 秒后重连、目录每秒复查：首次复查没赶上时约 2 秒恢复；累计等待要过 2 秒、又不超过原单次 3 秒超时。
            assertThat(total).isBetween(2250L, 2900L);
        }
    }

    /**
     * Triple 连接未就绪时同步调用得到的异常：Dubbo 把 UNAVAILABLE（网络错误）包成未知错误，原状态在异常链里。
     */
    private static RuntimeException unavailable() {
        return new RpcException(RpcException.UNKNOWN_EXCEPTION, "Fail to invoke remote method: resolve",
                new ExecutionException(TriRpcStatus.UNAVAILABLE.withDescription("upstream 192.0.2.10:50051 is unavailable").asException()));
    }

    private static RuntimeException noProvider() {
        return new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "No provider available for the service");
    }

    private static FleetJwtAuthenticationConverter converter(SecurityContextRpcService provider) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("securityContextRpcService", provider);
        return new FleetJwtAuthenticationConverter(new FleetOAuth2Properties(), new SecurityUserCache(null, Duration.ZERO),
                beans.getBeanProvider(SecurityContextRpcService.class), FAST);
    }

    private static SecurityContextResult.Status statusOf(Object token) {
        assertThat(token).isInstanceOf(FleetAuthenticationToken.class);
        return ((FleetAuthenticationToken) token).getStatus();
    }

    /**
     * 按顺序抛出给定失败，之后返回成功的业务上下文。
     */
    private static final class ScriptedProvider implements SecurityContextRpcService {

        private final Deque<RuntimeException> failures;

        private final AtomicInteger calls = new AtomicInteger();

        private ScriptedProvider(RuntimeException... failures) {
            this.failures = new ArrayDeque<>(List.of(failures));
        }

        @Override
        public SecurityContextResult resolve(String userId, String requestedTenantId) {
            calls.incrementAndGet();
            RuntimeException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
            return OK;
        }

        private int calls() {
            return calls.get();
        }
    }
}
