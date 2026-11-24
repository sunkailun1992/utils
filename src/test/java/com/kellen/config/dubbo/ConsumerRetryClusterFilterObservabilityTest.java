package com.kellen.config.dubbo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kellen.config.dubbo.RetryFixtures.FakeProvider;
import com.kellen.config.dubbo.RetryFixtures.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.TriRpcStatus;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static com.kellen.config.dubbo.RetryFixtures.unavailableAfterSend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集群层过滤器的可观测：重试、重试后成功与放弃各有计数（仿 Envoy 的 upstream_rq_retry 系列）；重试后成功记 INFO，
 * 放弃记 WARN，都不带参数值与异常消息；调用端第一次看到某个方法的幂等声明、或声明变了，记一行 INFO。
 */
class ConsumerRetryClusterFilterObservabilityTest {

    private static final String SECRET_ARGUMENT = "argument-must-not-appear";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(ConsumerRetryClusterFilter.class);

    private Level previousLevel;

    private FrameworkModel framework;

    private ApplicationModel application;

    private ModuleModel module;

    @BeforeEach
    void setUp() {
        framework = new FrameworkModel();
        application = framework.newApplication();
        module = application.getDefaultModule();
        Metrics.addRegistry(registry);
        logs.start();
        logger.addAppender(logs);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.setLevel(previousLevel);
        logger.detachAppender(logs);
        logs.stop();
        Metrics.removeRegistry(registry);
        registry.close();
        RpcContext.removeContext();
        framework.destroy();
    }

    @Test
    void recoveredCallCountsRetriesAndOneRecovery() {
        FakeProvider provider = new FakeProvider(provider(31001)).thenReturn(notSent()).thenReturn(notSent());

        cluster(consumer("retry-recovered"), provider).invoke(RetryFixtures.invocation("write"));

        assertThat(count(ConsumerRetryClusterFilter.RETRIES_METRIC, "layer", "unprocessed")).isEqualTo(2.0);
        assertThat(count(ConsumerRetryClusterFilter.RECOVERIES_METRIC, null, null)).isEqualTo(1.0);
        assertThat(provider.calls()).isEqualTo(3);
    }

    @Test
    void giveUpsCountTheirReason() {
        // attempts：幂等方法连续 3 次发出后失败。
        FakeProvider attempts = new FakeProvider(provider(31001, "read.idempotent", "true"))
                .thenThrow(unavailableAfterSend(), unavailableAfterSend(), unavailableAfterSend());
        assertThatThrownBy(() -> cluster(consumer("retry-attempts"), attempts).invoke(RetryFixtures.invocation("read")))
                .isInstanceOf(RpcException.class);

        // budget：5 次幂等调用各失败一次后重试成功，令牌 10→5.5；第 6 次失败后降到 4.5，不再重试。
        FakeProvider budget = new FakeProvider(provider(31002, "read.idempotent", "true"));
        for (int call = 0; call < 5; call++) {
            budget.thenThrow(unavailableAfterSend()).thenReturn(ok());
        }
        budget.thenThrow(unavailableAfterSend());
        Invoker<Service> budgetCluster = cluster(consumer("retry-budget"), budget);
        for (int call = 0; call < 5; call++) {
            budgetCluster.invoke(RetryFixtures.invocation("read"));
        }
        assertThatThrownBy(() -> budgetCluster.invoke(RetryFixtures.invocation("read"))).isInstanceOf(RpcException.class);

        // not_idempotent：没声明幂等的方法发出后失败。
        FakeProvider undeclared = new FakeProvider(provider(31003)).thenThrow(unavailableAfterSend());
        assertThatThrownBy(() -> cluster(consumer("retry-undeclared"), undeclared).invoke(RetryFixtures.invocation("write")))
                .isInstanceOf(RpcException.class);

        // deadline：150 毫秒超时内提供方一直不可用。
        FakeProvider gone = new FakeProvider(provider(31004)).availableAfter(Duration.ofHours(1));
        URL shortTimeout = RetryFixtures.consumerUrl(module, "timeout", "150", "retries", "0", "application", "retry-deadline");
        assertThatThrownBy(() -> cluster(shortTimeout, gone).invoke(RetryFixtures.invocation("write")))
                .isInstanceOf(RpcException.class);

        assertThat(count(ConsumerRetryClusterFilter.GIVE_UPS_METRIC, "reason", "attempts")).isEqualTo(1.0);
        assertThat(count(ConsumerRetryClusterFilter.GIVE_UPS_METRIC, "reason", "budget")).isEqualTo(1.0);
        assertThat(count(ConsumerRetryClusterFilter.GIVE_UPS_METRIC, "reason", "not_idempotent")).isEqualTo(1.0);
        assertThat(count(ConsumerRetryClusterFilter.GIVE_UPS_METRIC, "reason", "deadline")).isEqualTo(1.0);
    }

    @Test
    void recoveryIsLoggedAtInfoWithoutArguments() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true")).thenThrow(unavailableAfterSend());

        cluster(consumer("retry-log-recovery"), provider).invoke(invocationWithSecret("read"));

        List<ILoggingEvent> recoveries = events(Level.INFO, "后成功");
        assertThat(recoveries).hasSize(1);
        String message = recoveries.get(0).getFormattedMessage();
        assertThat(message).contains(RetryFixtures.INTERFACE + "#read", "重试 1 次后成功", "SENT_TRANSIENT", "UNAVAILABLE");
        assertThat(message).doesNotContain(SECRET_ARGUMENT, "wrapped");
    }

    @Test
    void giveUpIsLoggedAtWarnWithTheReason() {
        FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"))
                .thenThrow(unavailableAfterSend(), unavailableAfterSend(), unavailableAfterSend());

        assertThatThrownBy(() -> cluster(consumer("retry-log-giveup"), provider).invoke(invocationWithSecret("read")))
                .isInstanceOf(RpcException.class);

        List<ILoggingEvent> giveUps = events(Level.WARN, "放弃重试");
        assertThat(giveUps).hasSize(1);
        String message = giveUps.get(0).getFormattedMessage();
        assertThat(message).contains(RetryFixtures.INTERFACE + "#read", "attempts", "共尝试 3 次");
        assertThat(message).doesNotContain(SECRET_ARGUMENT, "wrapped");
    }

    @Test
    void declarationIsLoggedOncePerMethodAndValue() {
        Invoker<Service> declared = cluster(consumer("retry-log-declaration"),
                new FakeProvider(provider(31001, "read.idempotent", "true")));
        declared.invoke(RetryFixtures.invocation("read"));
        declared.invoke(RetryFixtures.invocation("read"));
        Invoker<Service> undeclared = cluster(consumer("retry-log-declaration"), new FakeProvider(provider(31002)));
        undeclared.invoke(RetryFixtures.invocation("read"));

        assertThat(events(Level.INFO, "幂等声明")).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "Dubbo 调用端 " + RetryFixtures.INTERFACE + "#read 的幂等声明：true",
                        "Dubbo 调用端 " + RetryFixtures.INTERFACE + "#read 的幂等声明：false");
    }

    /**
     * 新旧版本或灰度与稳定实例的声明不一致时，调用会在两种值之间来回：每个取值只记一次，不随每次翻转刷屏。
     */
    @Test
    void declarationIsLoggedOncePerValueEvenWhenItFlips() {
        Invoker<Service> declared = cluster(consumer("retry-log-flip"),
                new FakeProvider(provider(31001, "read.idempotent", "true")));
        Invoker<Service> undeclared = cluster(consumer("retry-log-flip"), new FakeProvider(provider(31002)));

        for (int round = 0; round < 3; round++) {
            declared.invoke(RetryFixtures.invocation("read"));
            undeclared.invoke(RetryFixtures.invocation("read"));
        }

        assertThat(events(Level.INFO, "幂等声明")).hasSize(2);
    }

    /**
     * 取结果里带回的失败时不经 {@code AsyncRpcResult#getAppResponse}，免得 Dubbo 为同一次失败再记一条 ERROR。
     */
    @Test
    void failuresInResultsDoNotAddDubboErrorLogs() {
        Logger dubboResult = (Logger) LoggerFactory.getLogger(AsyncRpcResult.class);
        ListAppender<ILoggingEvent> dubboLogs = new ListAppender<>();
        dubboLogs.start();
        dubboResult.addAppender(dubboLogs);
        try {
            FakeProvider provider = new FakeProvider(provider(31001, "read.idempotent", "true"))
                    .thenReturn(invocation -> AsyncRpcResult.newDefaultAsyncResult(TriRpcStatus.CANCELLED.asException(), invocation));

            cluster(consumer("retry-no-extra-error"), provider).invoke(RetryFixtures.invocation("read"));

            assertThat(provider.calls()).isEqualTo(2);
            assertThat(dubboLogs.list).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
        } finally {
            dubboResult.detachAppender(dubboLogs);
            dubboLogs.stop();
        }
    }

    private double count(String metric, String tagKey, String tagValue) {
        var search = registry.find(metric);
        if (tagKey != null) {
            search = search.tag(tagKey, tagValue);
        }
        return search.counters().stream().mapToDouble(Counter::count).sum();
    }

    private List<ILoggingEvent> events(Level level, String text) {
        return logs.list.stream()
                .filter(event -> event.getLevel() == level && event.getFormattedMessage().contains(text))
                .toList();
    }

    /**
     * 模拟协议层过滤器判定的“未发出”：标记后抛网络错误。
     */
    private static Function<Invocation, Result> notSent() {
        return invocation -> {
            invocation.put(UnprocessedRequestFilter.NOT_SENT, Boolean.TRUE);
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "请求未发出（测试）");
        };
    }

    private static Function<Invocation, Result> ok() {
        return invocation -> AsyncRpcResult.newDefaultAsyncResult("ok", invocation);
    }

    private static RpcInvocation invocationWithSecret(String method) {
        return new RpcInvocation(null, method, RetryFixtures.INTERFACE, RetryFixtures.INTERFACE,
                new Class<?>[] {String.class}, new Object[] {SECRET_ARGUMENT});
    }

    private URL consumer(String application) {
        return RetryFixtures.consumerUrl(module, "timeout", "3000", "retries", "0", "application", application);
    }

    private URL provider(int port, String... keyValues) {
        return RetryFixtures.providerUrl(module, port, keyValues);
    }

    @SafeVarargs
    private Invoker<Service> cluster(URL consumer, Invoker<Service>... providers) {
        return RetryFixtures.cluster(application, consumer, providers);
    }
}
