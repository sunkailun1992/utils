package com.kellen.config.mq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后置处理器：给 RocketMQTemplate 的生产者与监听容器的消费者挂上收发钩子；转换器缺省忽略未知字段。
 */
class MqRocketMqInitializerTest {

    /** 测试主题与消费组。 */
    static final String TOPIC = "github-dev-utils-test-topic";
    static final String GROUP = "github-dev-utils-test-group";

    @Test
    void initializerRegistersHooksOnTemplateAndContainer() throws Exception {
        MqRocketMqInitializer initializer = new MqRocketMqInitializer(new MockEnvironment());
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(new DefaultMQProducer("github-dev-utils-test-producer"));
        initializer.postProcessAfterInitialization(template, "rocketMQTemplate");
        assertThat(template.getProducer().getDefaultMQProducerImpl().hasSendMessageHook()).isTrue();

        DefaultRocketMQListenerContainer container = container();
        initializer.postProcessBeforeInitialization(container, "container");
        container.afterPropertiesSet();
        initializer.postProcessAfterInitialization(container, "container");
        assertThat(container.getConsumer().getDefaultMQPushConsumerImpl().hasHook()).isTrue();
    }

    /**
     * rocketmq-spring 缺省的转换器（Spring 的 MappingJackson2MessageConverter 已关掉 FAIL_ON_UNKNOWN_PROPERTIES）忽略未知字段：
     * 生产方以后给事件加字段，旧消费方照常消费。锁住这一行为，依赖升级改了缺省时这里会先失败。
     */
    @Test
    void defaultConverterIgnoresUnknownFields() {
        Object converted = new RocketMQMessageConverter().getMessageConverter()
                .fromMessage(MessageBuilder.withPayload("{\"eventId\":\"e\",\"foo\":1}").build(), Probe.class);
        assertThat(converted).isInstanceOf(Probe.class);
        assertThat(((Probe) converted).getEventId()).isEqualTo("e");
    }

    /** 建一个未启动的监听容器：主题、消费组、监听器与注解齐全，不连 broker。 */
    static DefaultRocketMQListenerContainer container() {
        DefaultRocketMQListenerContainer container = new DefaultRocketMQListenerContainer();
        container.setApplicationContext(new StaticApplicationContext());
        container.setNameServer("127.0.0.1:9876");
        container.setTopic(TOPIC);
        container.setConsumerGroup(GROUP);
        container.setRocketMQListener(new StringListener());
        container.setRocketMQMessageListener(StringListener.class.getAnnotation(RocketMQMessageListener.class));
        return container;
    }

    /** 测试用监听器，只提供注解与消息类型。 */
    @RocketMQMessageListener(topic = TOPIC, consumerGroup = GROUP)
    static class StringListener implements RocketMQListener<String> {

        @Override
        public void onMessage(String message) {
        }
    }

    /** 反序列化目标：只有 eventId 一个字段。 */
    static class Probe {

        private String eventId;

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }
    }
}
