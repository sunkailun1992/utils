package com.kellen.config.mq;

import com.kellen.utils.mq.MqTemplate;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * fleet 的 RocketMQ 装配：只在服务配了 {@code rocketmq.name-server} 时生效。
 *
 * <p>没配的服务（不收发消息的那些）这里什么都不建，与 rocketmq-spring 自己不建生产者、监听容器一致。</p>
 */
@AutoConfiguration(after = RocketMQAutoConfiguration.class)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class MqAutoConfiguration {

    /**
     * 由 Spring Boot 自动配置机制创建。
     */
    public MqAutoConfiguration() {
    }

    /**
     * 本实例的泳道与灰度标签。
     *
     * @param environment Spring 环境
     * @return 泳道与灰度标签
     */
    @Bean
    @ConditionalOnMissingBean
    public MqInstanceLane mqInstanceLane(Environment environment) {
        return MqInstanceLane.resolve(environment);
    }

    /**
     * 发送工具；只有配了生产者（{@code rocketmq.producer.group}）的服务才有。
     *
     * @param rocketMQTemplate rocketmq-spring 模板
     * @param mqInstanceLane   本实例的泳道与灰度标签
     * @return 发送工具
     */
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean
    public MqTemplate mqTemplate(RocketMQTemplate rocketMQTemplate, MqInstanceLane mqInstanceLane) {
        return new MqTemplate(rocketMQTemplate, mqInstanceLane);
    }
}
