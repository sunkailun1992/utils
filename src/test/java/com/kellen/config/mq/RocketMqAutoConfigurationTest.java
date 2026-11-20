package com.kellen.config.mq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rocketmq-spring 2.3.6 在 Spring Boot 4.0.9 上的装配：没配地址时不建生产者，配了才有 {@link RocketMQTemplate}；客户端锁 5.5.1。
 */
class RocketMqAutoConfigurationTest {

    /** 只装 rocketmq-spring 自己的自动配置，与服务里的装配方式一致。 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RocketMQAutoConfiguration.class));

    /** 不收发消息的服务不配 {@code rocketmq.*}，不能建出生产者或模板，也就不会连 RocketMQ。 */
    @Test
    void noRocketMqBeansWithoutNameServer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DefaultMQProducer.class);
            assertThat(context).doesNotHaveBean(RocketMQTemplate.class);
        });
    }

    /** 配了地址与生产者组时，Boot 4 上下文能装配出模板；只启动客户端，不需要真的连上 broker。 */
    @Test
    void templateCreatedWhenConfigured() {
        runner.withPropertyValues("rocketmq.name-server=127.0.0.1:9876",
                        "rocketmq.producer.group=github-dev-utils-test-producer")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RocketMQTemplate.class);
                });
    }

    /** NAS 上的 broker 开了 ACL 2.0：配了账号时生产者带签名钩子；5.5.1 的签名类在 rocketmq-client 里，rocketmq-acl 已排除。 */
    @Test
    void templateWithAclCreatedWhenConfigured() throws ClassNotFoundException {
        runner.withPropertyValues("rocketmq.name-server=127.0.0.1:9876",
                        "rocketmq.producer.group=github-dev-utils-test-producer",
                        "rocketmq.producer.access-key=github-dev-utils-test",
                        "rocketmq.producer.secret-key=synthetic-not-a-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RocketMQTemplate.class);
                });
        Class<?> hook = Class.forName("org.apache.rocketmq.acl.common.AclClientRPCHook");
        assertThat(hook.getProtectionDomain().getCodeSource().getLocation().toString()).contains("rocketmq-client-5.5.1");
    }

    /** 客户端与 NAS 上的服务端同一版本线（5.5），且 5.5.1 的公共模块不再依赖 fastjson 1.x。 */
    @Test
    void clientVersionIs551() {
        assertThat(MQVersion.getVersionDesc(MQVersion.CURRENT_VERSION)).isEqualTo("V5_5_1");
    }
}
