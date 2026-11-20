package com.kellen.config.mq;

import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.TenantContextHolder;
import com.kellen.utils.mq.MqSendException;
import com.kellen.utils.mq.MqSendOptions;
import com.kellen.utils.mq.MqTemplate;
import org.apache.rocketmq.client.impl.consumer.RebalanceImpl;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 连 NAS 上的 RocketMQ 5.5 验证 utils 消息模块：上下文随消息传递、泳道过滤、延时、顺序、错误口令被拒。
 *
 * <p>只在设置了 {@code FLEET_MQ_TEST_NAMESRV} 时运行（变量在本机私有的 fleet-mq-test.env 里），用测试账号
 * {@code github-dev-utils-test}：只能收发两个测试主题、只能用 {@code github-dev-utils-test-} 开头的消费组。
 * 消费组名带本次运行的时间戳，消息内容带本次运行的标记；新消费组可能从头读到以前运行留下的消息，断言只看本次发出的。</p>
 */
@EnabledIfEnvironmentVariable(named = "FLEET_MQ_TEST_NAMESRV", matches = ".+")
class RocketMqLiveTest {

    /** 普通测试主题。 */
    static final String TOPIC = "github-dev-utils-live-test";

    /** 顺序测试主题。 */
    static final String ORDERLY_TOPIC = "github-dev-utils-live-test-orderly";

    /** 本次运行的标记（新加坡时间，到毫秒）。 */
    private static final String RUN = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
            .format(LocalDateTime.now(ZoneId.of("Asia/Singapore")));

    /** 真实的 rocketmq-spring 与 utils 自动配置，生产者与消费者都用测试账号。 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RocketMQAutoConfiguration.class, MqAutoConfiguration.class))
            .withPropertyValues(
                    "rocketmq.name-server=" + System.getenv("FLEET_MQ_TEST_NAMESRV"),
                    "rocketmq.producer.access-key=" + System.getenv("FLEET_MQ_TEST_ACCESS_KEY"),
                    "rocketmq.producer.secret-key=" + System.getenv("FLEET_MQ_TEST_SECRET_KEY"),
                    "rocketmq.consumer.access-key=" + System.getenv("FLEET_MQ_TEST_ACCESS_KEY"),
                    "rocketmq.consumer.secret-key=" + System.getenv("FLEET_MQ_TEST_SECRET_KEY"));

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        UserContextHolder.clear();
    }

    @Test
    void sendAndReceiveWithContext() {
        runner.withPropertyValues(producerGroup("ctx"), consumerGroup("ctx"))
                .withBean(TopicListener.class)
                .run(context -> {
                    awaitAssigned(context, TOPIC);
                    String id = RUN + "-ctx";
                    TenantContextHolder.setTenantId("tenant-live");
                    UserContextHolder.set(new SecurityUser("user-live", "live", "tenant-live", List.of()));
                    try {
                        context.getBean(MqTemplate.class).send(TOPIC, id);
                    } finally {
                        TenantContextHolder.clear();
                        UserContextHolder.clear();
                    }

                    Received received = awaitReceived(context.getBean(TopicListener.class), id, Duration.ofSeconds(30));
                    assertThat(received.tenantId()).isEqualTo("tenant-live");
                    assertThat(received.userId()).isEqualTo("user-live");
                });
    }

    @Test
    void canaryMessageSkipsStableConsumer() {
        runner.withPropertyValues(producerGroup("stable"), consumerGroup("lane"))
                .withBean(TopicListener.class)
                .run(stable -> runner.withPropertyValues(producerGroup("canary"), consumerGroup("lane"),
                                "spring.cloud.nacos.discovery.metadata[traffic.lane]=canary",
                                "spring.cloud.nacos.discovery.metadata[canary.tag]=kellen-local")
                        .withBean(TopicListener.class)
                        .run(canary -> {
                            assertThat(canary.getBean(DefaultRocketMQListenerContainer.class).getConsumerGroup())
                                    .isEqualTo(groupName("lane") + "-canary-kellen-local");
                            awaitAssigned(stable, TOPIC);
                            awaitAssigned(canary, TOPIC);
                            TopicListener stableListener = stable.getBean(TopicListener.class);
                            TopicListener canaryListener = canary.getBean(TopicListener.class);
                            String canaryId = RUN + "-lane-canary";
                            String stableId = RUN + "-lane-stable";

                            canary.getBean(MqTemplate.class).send(TOPIC, canaryId);
                            stable.getBean(MqTemplate.class).send(TOPIC, stableId);

                            awaitReceived(canaryListener, canaryId, Duration.ofSeconds(30));
                            awaitReceived(stableListener, stableId, Duration.ofSeconds(30));
                            await().during(Duration.ofSeconds(10)).atMost(Duration.ofSeconds(11))
                                    .until(() -> !stableListener.received.containsKey(canaryId)
                                            && !canaryListener.received.containsKey(stableId));
                        }));
    }

    @Test
    void delayDelaysDelivery() {
        runner.withPropertyValues(producerGroup("delay"), consumerGroup("delay"))
                .withBean(TopicListener.class)
                .run(context -> {
                    awaitAssigned(context, TOPIC);
                    String id = RUN + "-delay";
                    long sentAt = System.nanoTime();
                    context.getBean(MqTemplate.class).send(TOPIC, id, MqSendOptions.none().withDelay(Duration.ofSeconds(5)));

                    Received received = awaitReceived(context.getBean(TopicListener.class), id, Duration.ofSeconds(30));
                    assertThat(Duration.ofNanos(received.at() - sentAt)).isGreaterThanOrEqualTo(Duration.ofMillis(4500));
                });
    }

    @Test
    void orderKeyKeepsOrder() {
        runner.withPropertyValues(producerGroup("orderly"), consumerGroup("orderly"))
                .withBean(OrderlyListener.class)
                .run(context -> {
                    awaitAssigned(context, ORDERLY_TOPIC);
                    List<String> sent = IntStream.range(0, 20).mapToObj(i -> RUN + "-orderly-" + i).toList();
                    MqTemplate template = context.getBean(MqTemplate.class);
                    for (String id : sent) {
                        template.send(ORDERLY_TOPIC, id, MqSendOptions.none().withOrderKey(RUN));
                    }

                    OrderlyListener listener = context.getBean(OrderlyListener.class);
                    await().atMost(Duration.ofSeconds(30)).until(() -> listener.received.keySet().containsAll(sent));
                    assertThat(listener.order.stream().filter(sent::contains).distinct().toList()).containsExactlyElementsOf(sent);
                });
    }

    @Test
    void wrongSecretIsRejected() {
        runner.withPropertyValues(producerGroup("right"))
                .run(context -> context.getBean(MqTemplate.class).send(TOPIC, RUN + "-right")); // 对照：同一账号、正确口令能发出。
        runner.withPropertyValues(producerGroup("wrong"), "rocketmq.producer.secret-key=wrong-" + UUID.randomUUID())
                .run(context -> assertThatThrownBy(() -> context.getBean(MqTemplate.class).send(TOPIC, RUN + "-wrong"))
                        .isInstanceOf(MqSendException.class));
    }

    /** 本次运行某个用例的消费组名（未加泳道后缀）。 */
    private static String groupName(String name) {
        return "github-dev-utils-test-" + RUN + "-" + name;
    }

    private static String consumerGroup(String name) {
        return "fleet.mq.test.group=" + groupName(name);
    }

    private static String producerGroup(String name) {
        return "rocketmq.producer.group=github-dev-utils-test-" + RUN + "-producer-" + name;
    }

    /** 等消费者完成负载均衡、拿到主题的全部队列：此后发出的消息一定在它的起始位点之后。 */
    private static void awaitAssigned(ApplicationContext context, String topic) {
        RebalanceImpl rebalance = context.getBean(DefaultRocketMQListenerContainer.class)
                .getConsumer().getDefaultMQPushConsumerImpl().getRebalanceImpl();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            Set<MessageQueue> queues = rebalance.getTopicSubscribeInfoTable().get(topic);
            long assigned = rebalance.getProcessQueueTable().keySet().stream().filter(q -> topic.equals(q.getTopic())).count();
            return queues != null && !queues.isEmpty() && assigned == queues.size();
        });
    }

    private static Received awaitReceived(RecordingListener listener, String id, Duration timeout) {
        await().atMost(timeout).until(() -> listener.received.containsKey(id));
        return listener.received.get(id);
    }

    /** 收到一条消息时的时刻（System.nanoTime）与消费线程上的租户、用户。 */
    record Received(long at, String tenantId, String userId) {
    }

    /** 记下收到的消息内容（按到达顺序）与消费时线程上的上下文。 */
    abstract static class RecordingListener implements RocketMQListener<String> {

        final Map<String, Received> received = new ConcurrentHashMap<>();

        final List<String> order = new CopyOnWriteArrayList<>();

        @Override
        public void onMessage(String message) {
            received.putIfAbsent(message, new Received(System.nanoTime(), TenantContextHolder.getTenantId(), UserContextHolder.getUserId()));
            order.add(message);
        }
    }

    /** 普通主题的监听器；消费组由各用例的 {@code fleet.mq.test.group} 给出。 */
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = "${fleet.mq.test.group}")
    static class TopicListener extends RecordingListener {
    }

    /** 顺序主题的顺序消费监听器。 */
    @RocketMQMessageListener(topic = ORDERLY_TOPIC, consumerGroup = "${fleet.mq.test.group}", consumeMode = ConsumeMode.ORDERLY)
    static class OrderlyListener extends RecordingListener {
    }
}
