package com.kellen.config.dubbo;

import com.kellen.config.dubbo.RetryFixtures.EmptyDirectory;
import com.kellen.config.dubbo.RetryFixtures.FakeProvider;
import com.kellen.config.dubbo.RetryFixtures.Service;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.client.migration.MigrationInvoker;
import org.apache.dubbo.registry.client.migration.model.MigrationStep;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 经注册中心的引用：集群调用端外面还有 Dubbo 的 {@link MigrationInvoker}，默认 {@link MigrationStep#APPLICATION_FIRST}
 * 下应用级地址不可用时改走接口级地址，统一重试在每个集群调用端里面，只能重试被选中的那个。
 *
 * <p>这里用真实的 {@link MigrationInvoker} 包两个集群调用端（应用级、接口级），锁定两种注册方式下的行为：提供方两级都注册
 * （Dubbo 默认 {@code register-mode=all}）时第一层能等到恢复；只注册应用级地址时立即失败、不等——这是本方案的前提与已知边界，
 * 写在 {@code docs/ai-coding/RPC_API_CODING_SPEC.md}「调用端统一重试」。</p>
 */
class ConsumerRetryRegistryMigrationTest {

    private FrameworkModel framework;

    private ApplicationModel application;

    private ModuleModel module;

    @BeforeEach
    void newModel() {
        framework = new FrameworkModel();
        application = framework.newApplication();
        module = application.getDefaultModule();
    }

    @AfterEach
    void destroyModel() {
        RpcContext.removeContext();
        framework.destroy();
    }

    @Test
    void providerRegisteredAtBothLevelsIsWaitedFor() {
        FakeProvider applicationLevel = new FakeProvider(RetryFixtures.providerUrl(module, 31001))
                .availableAfter(Duration.ofMillis(300));
        FakeProvider interfaceLevel = new FakeProvider(RetryFixtures.providerUrl(module, 31001))
                .availableAfter(Duration.ofMillis(300));

        Result result = migration(cluster(interfaceLevel), cluster(applicationLevel)).invoke(RetryFixtures.invocation("write"));

        assertThat(result.getValue()).isEqualTo("ok:write");
        assertThat(applicationLevel.calls() + interfaceLevel.calls()).isEqualTo(1);
    }

    @Test
    void providerRegisteredAtApplicationLevelOnlyIsNotWaitedFor() {
        FakeProvider applicationLevel = new FakeProvider(RetryFixtures.providerUrl(module, 31001))
                .availableAfter(Duration.ofMillis(300));
        ClusterInvoker<Service> interfaceLevel =
                (ClusterInvoker<Service>) RetryFixtures.join(new EmptyDirectory(consumer().setScopeModel(module)));

        assertThatThrownBy(() -> migration(interfaceLevel, cluster(applicationLevel)).invoke(RetryFixtures.invocation("write")))
                .isInstanceOfSatisfying(RpcException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER));
        assertThat(applicationLevel.calls()).isZero();
    }

    private MigrationInvoker<Service> migration(ClusterInvoker<Service> interfaceLevel, ClusterInvoker<Service> applicationLevel) {
        URL consumer = consumer().setScopeModel(module);
        MigrationInvoker<Service> migration = new MigrationInvoker<>(
                interfaceLevel, applicationLevel, null, null, null, Service.class, consumer, consumer) {
            {
                // 与 MigrationInvoker#calcPreferredInvoker 在应用级地址够用时的选择相同。
                setCurrentAvailableInvoker(applicationLevel);
            }
        };
        migration.setMigrationStep(MigrationStep.APPLICATION_FIRST);
        return migration;
    }

    private URL consumer() {
        return RetryFixtures.consumerUrl(module, "timeout", "3000", "retries", "0");
    }

    private ClusterInvoker<Service> cluster(FakeProvider provider) {
        return (ClusterInvoker<Service>) RetryFixtures.cluster(application, consumer(), provider);
    }
}
