package com.kellen.config.dubbo;

import com.kellen.observability.fleet.TrafficRouteObservation;
import com.kellen.traffic.CanaryContext;
import com.kellen.traffic.CanaryGrant;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.filter.DefaultFilterChainBuilder;
import org.apache.dubbo.rpc.cluster.router.tag.TagStateRouter;
import org.apache.dubbo.rpc.cluster.router.tag.model.TagRouterRule;
import org.apache.dubbo.rpc.cluster.router.tag.model.TagRuleParser;
import org.apache.dubbo.rpc.cluster.support.FailoverCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class DubboCanaryRoutingTest {

    private static final String INTERFACE = TestService.class.getName();
    private static final String CANARY_TAG = "blue-v2";
    private static final String GRANT_TOKEN = "test-signed-grant";
    private final List<Invoker<TestService>> clusters = new ArrayList<>();
    private MockedStatic<TrafficRouteObservation> observation;

    @BeforeEach
    void captureRouteDecisions() {
        observation = mockStatic(TrafficRouteObservation.class);
    }

    @AfterEach
    void clearContext() {
        CanaryContext.clear();
        RpcContext.removeContext();
        clusters.forEach(Invoker::destroy);
        observation.close();
    }

    @Test
    void scopedCallRestoresAnExistingGrantWhenActionFails() {
        bindCanary(Instant.now().plusSeconds(30));
        CanaryContext.Snapshot original = CanaryContext.get();
        assertThatThrownBy(() -> CanaryContext.callWith(null, () -> {
            assertThat(CanaryContext.get()).isNull();
            throw new IllegalStateException("fixture failure");
        })).hasMessage("fixture failure");
        assertThat(CanaryContext.get()).isSameAs(original);
    }

    @Test
    void selectsMatchingProviderAndCarriesGrantBeforeReturningToStableOnSameThread() {
        RecordingProvider stable = new RecordingProvider("stable", 31001, null);
        RecordingProvider canary = new RecordingProvider("canary", 31002, CANARY_TAG);
        Invoker<TestService> cluster = cluster(true, stable, canary);
        bindCanary(Instant.now().plusSeconds(120));

        assertThat(cluster.invoke(invocation()).getValue()).isEqualTo("canary");
        assertThat(canary.grants).containsExactly(GRANT_TOKEN);
        CanaryContext.clear();

        assertThat(cluster.invoke(invocation()).getValue()).isEqualTo("stable");
        assertThat(stable.grants).containsExactly((String) null);
        assertThat(canary.grants).hasSize(1);
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "canary", "selected"));
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "stable", "selected"));
        observation.verifyNoMoreInteractions();
    }

    @Test
    void unselectedReferenceDoesNotForwardAnAutomaticallyInheritedGrant() {
        RecordingProvider stable = new RecordingProvider("stable", 31001, null);
        RecordingProvider canary = new RecordingProvider("canary", 31002, CANARY_TAG);
        Invoker<TestService> cluster = cluster(false, stable, canary);
        bindCanary(Instant.now().plusSeconds(120));
        RpcContext.getServerAttachment().setAttachment(CanaryGrant.ATTACHMENT, GRANT_TOKEN);

        assertThat(cluster.invoke(invocation()).getValue()).isEqualTo("stable");
        assertThat(stable.grants).containsExactly((String) null);
        assertThat(canary.grants).isEmpty();
        observation.verifyNoInteractions();
    }

    @Test
    void missingMatchingProviderNeverFallsBackToStable() {
        RecordingProvider stable = new RecordingProvider("stable", 31001, null);
        Invoker<TestService> cluster = cluster(true, stable);
        bindCanary(Instant.now().plusSeconds(120));

        assertThatThrownBy(() -> cluster.invoke(invocation())).isInstanceOf(RpcException.class);
        assertThat(stable.grants).isEmpty();
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "canary", "unavailable"));
        observation.verifyNoMoreInteractions();
    }

    @Test
    void expiredGrantFailsBeforeEitherProviderIsCalled() {
        RecordingProvider stable = new RecordingProvider("stable", 31001, null);
        RecordingProvider canary = new RecordingProvider("canary", 31002, CANARY_TAG);
        Invoker<TestService> cluster = cluster(true, stable, canary);
        bindCanary(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> cluster.invoke(invocation())).isInstanceOf(RpcException.class);
        assertThat(stable.grants).isEmpty();
        assertThat(canary.grants).isEmpty();
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "canary", "rejected"));
        observation.verifyNoMoreInteractions();
    }

    @Test
    void transportFailureAfterSelectionDoesNotEmitASecondDecision() {
        RecordingProvider stable = new RecordingProvider("stable", 31001, null, true);
        Invoker<TestService> cluster = cluster(true, stable);

        assertThatThrownBy(() -> cluster.invoke(invocation())).isInstanceOf(RpcException.class);
        assertThat(stable.grants).hasSize(1);
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "stable", "selected"));
        observation.verifyNoMoreInteractions();
    }

    @Test
    void optedInStableReferenceWithNoStableProviderReportsUnavailable() {
        RecordingProvider canary = new RecordingProvider("canary", 31002, CANARY_TAG);
        Invoker<TestService> cluster = cluster(true, canary);

        assertThatThrownBy(() -> cluster.invoke(invocation())).isInstanceOf(RpcException.class);
        assertThat(canary.grants).isEmpty();
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "stable", "unavailable"));
        observation.verifyNoMoreInteractions();
    }

    @Test
    void conflictingDynamicTagRuleNeverInvokesAnUntaggedProvider() {
        RecordingProvider stable = new RecordingProvider("stable", 31001, null);
        RecordingProvider canary = new RecordingProvider("canary", 31002, CANARY_TAG);
        StaticDirectory<TestService> directory = directory(true, stable, canary);
        TagRouterRule rule = TagRuleParser.parse("""
                configVersion: v2.7
                key: test-provider
                enabled: true
                force: true
                runtime: true
                tags:
                  - name: blue-v2
                    addresses: ["192.0.2.1:31001"]
                """);
        TagStateRouter<?> router = directory.getRouterChain().getCurrentChain().getStateRouters().stream()
                .filter(TagStateRouter.class::isInstance)
                .map(TagStateRouter.class::cast)
                .findFirst()
                .orElseThrow();
        rule.init(router);
        router.setTagRouterRule(rule);
        Invoker<TestService> cluster = join(directory);
        bindCanary(Instant.now().plusSeconds(120));

        try {
            assertThatThrownBy(() -> cluster.invoke(invocation()))
                    .isInstanceOf(RpcException.class)
                    .hasRootCauseMessage("Selected Dubbo provider does not match the canary grant");
        } finally {
            router.setTagRouterRule(null);
        }
        assertThat(stable.grants).isEmpty();
        assertThat(canary.grants).isEmpty();
        observation.verify(() -> TrafficRouteObservation.record("dubbo_consumer", "canary", "rejected"));
        observation.verifyNoMoreInteractions();
    }

    private Invoker<TestService> cluster(boolean canaryEnabled, RecordingProvider... providers) {
        return join(directory(canaryEnabled, providers));
    }

    private Invoker<TestService> join(StaticDirectory<TestService> directory) {
        Invoker<TestService> cluster = new FailoverCluster().join(directory, true);
        clusters.add(cluster);
        return cluster;
    }

    private StaticDirectory<TestService> directory(boolean canaryEnabled, RecordingProvider... providers) {
        URL consumer = new URL("consumer", "127.0.0.1", 0, INTERFACE, Map.of(
                "side", "consumer", "application", "test-consumer", "interface", INTERFACE,
                "retries", "0", "traffic.canary.enabled", Boolean.toString(canaryEnabled)));
        DefaultFilterChainBuilder builder = new DefaultFilterChainBuilder();
        List<Invoker<TestService>> providerInvokers = new ArrayList<>();
        for (RecordingProvider provider : providers) {
            providerInvokers.add(builder.buildInvokerChain(
                    provider, CommonConstants.REFERENCE_FILTER_KEY, CommonConstants.CONSUMER));
        }
        StaticDirectory<TestService> directory = new StaticDirectory<>(consumer, providerInvokers);
        directory.buildRouterChain();
        return directory;
    }

    private RpcInvocation invocation() {
        return new RpcInvocation(null, "identity", INTERFACE, INTERFACE, new Class<?>[0], new Object[0]);
    }

    private void bindCanary(Instant expiresAt) {
        CanaryGrant grant = new CanaryGrant("test-provider", CANARY_TAG, expiresAt);
        CanaryContext.set(new CanaryContext.Snapshot(grant, GRANT_TOKEN));
    }

    interface TestService {
        String identity();
    }

    private static final class RecordingProvider implements Invoker<TestService> {

        private final String identity;
        private final URL url;
        private final boolean unavailable;
        private final List<String> grants = new ArrayList<>();

        private RecordingProvider(String identity, int port, String tag) {
            this(identity, port, tag, false);
        }

        private RecordingProvider(String identity, int port, String tag, boolean unavailable) {
            this.identity = identity;
            this.unavailable = unavailable;
            URL address = new URL("dubbo", "192.0.2.1", port, INTERFACE,
                    Map.of("side", "consumer", "application", "test-provider", "interface", INTERFACE));
            this.url = tag == null ? address : address.addParameter(CommonConstants.TAG_KEY, tag);
        }

        @Override
        public Class<TestService> getInterface() {
            return TestService.class;
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public Result invoke(Invocation invocation) {
            grants.add(invocation.getAttachment(CanaryGrant.ATTACHMENT));
            if (unavailable) {
                throw new RpcException(RpcException.NETWORK_EXCEPTION, "fixture transport failed");
            }
            return AsyncRpcResult.newDefaultAsyncResult(identity, invocation);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() {
        }
    }
}
