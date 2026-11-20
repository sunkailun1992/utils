package com.kellen.config.mq;

import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;

/**
 * rocketmq-spring 的 Bean 后置处理器（照芋道的 {@code TenantRocketMQInitializer}）：
 * 给 {@link RocketMQTemplate} 的生产者挂发送钩子，给每个监听容器的消费者挂消费钩子。
 */
public class MqRocketMqInitializer implements BeanPostProcessor {

    /** 读本实例泳道用的环境；后置处理器创建得早，泳道在第一次用到时才解析。 */
    private final Environment environment;

    /** 本实例的泳道与灰度标签。 */
    private volatile MqInstanceLane lane;

    /**
     * 创建后置处理器。
     *
     * @param environment Spring 环境
     */
    public MqRocketMqInitializer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof RocketMQTemplate template) {
            registerSendHook(template.getProducer());
        } else if (bean instanceof DefaultRocketMQListenerContainer container) {
            registerConsumeHook(container);
        }
        return bean;
    }

    private void registerSendHook(DefaultMQProducer producer) {
        DefaultMQProducerImpl impl = producer == null ? null : producer.getDefaultMQProducerImpl();
        if (impl != null) {
            impl.registerSendMessageHook(new MqContextSendMessageHook(lane()));
        }
    }

    private void registerConsumeHook(DefaultRocketMQListenerContainer container) {
        DefaultMQPushConsumerImpl impl = container.getConsumer() == null ? null : container.getConsumer().getDefaultMQPushConsumerImpl();
        if (impl != null) {
            impl.registerConsumeMessageHook(new MqContextConsumeMessageHook());
        }
    }

    /**
     * 本实例的泳道与灰度标签。
     *
     * @return 泳道与灰度标签
     */
    MqInstanceLane lane() {
        MqInstanceLane current = lane;
        if (current == null) {
            current = MqInstanceLane.resolve(environment);
            lane = current;
        }
        return current;
    }
}
