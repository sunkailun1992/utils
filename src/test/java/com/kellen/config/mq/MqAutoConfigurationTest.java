package com.kellen.config.mq;

import com.kellen.utils.mq.MqTemplate;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fleet 的 RocketMQ 装配只在配了地址时生效；配了生产者组才有发送工具。
 */
class MqAutoConfigurationTest {

    /** rocketmq-spring 与 fleet 装配按服务里的顺序加载。 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RocketMQAutoConfiguration.class, MqAutoConfiguration.class));

    @Test
    void nothingCreatedWithoutNameServer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MqInstanceLane.class);
            assertThat(context).doesNotHaveBean(MqTemplate.class);
        });
    }

    @Test
    void templateCreatedWithProducerGroup() {
        runner.withPropertyValues("rocketmq.name-server=127.0.0.1:9876",
                        "rocketmq.producer.group=github-dev-utils-test-producer",
                        "spring.cloud.nacos.discovery.metadata[traffic.lane]=canary",
                        "spring.cloud.nacos.discovery.metadata[canary.tag]=kellen-local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MqTemplate.class);
                    assertThat(context.getBean(MqInstanceLane.class)).isEqualTo(new MqInstanceLane("canary", "kellen-local"));
                });
    }

    @Test
    void consumerOnlyServiceHasLaneButNoTemplate() {
        runner.withPropertyValues("rocketmq.name-server=127.0.0.1:9876")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MqInstanceLane.class);
                    assertThat(context).doesNotHaveBean(MqTemplate.class);
                });
    }
}
