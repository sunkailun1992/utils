package com.kellen.config.rabbit;

import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import com.kellen.utils.datasource.DataSourceUtil;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.BaseRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.DirectRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 公共增强配置。
 *
 * <p>连接、账号、虚拟主机、确认、重试、prefetch 等基础能力仍交给 Spring Boot 和
 * {@code spring.rabbitmq.*} 配置管理；这里仅负责 JSON 转换和跨 MQ 链路上下文透传。</p>
 *
 * @author 孙凯伦
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RabbitTemplate.class, ConnectionFactory.class})
@EnableConfigurationProperties(TenantProperties.class)
public class RabbitMqConfig {

    /**
     * 日志链路追踪头。
     */
    private static final String TRACE_ID = "traceId";

    /**
     * RabbitMQ JSON 消息转换器。
     *
     * @return 消息转换器
     */
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * 发送消息前写入通用上下文。
     *
     * @param tenantProperties 租户配置
     * @return 发送前处理器
     */
    @Bean
    @ConditionalOnMissingBean(name = "rabbitContextPublishPostProcessor")
    public MessagePostProcessor rabbitContextPublishPostProcessor(TenantProperties tenantProperties) {
        return new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                Map<String, Object> headers = message.getMessageProperties().getHeaders();
                putHeader(headers, DataSourceUtil.DATA_SOURCE, DynamicSourceTtl.get());
                putHeader(headers, TRACE_ID, MDC.get(TRACE_ID));
                if (tenantProperties.isEnabled()) {
                    putHeader(headers, tenantHeaderName(tenantProperties), TenantContextHolder.getTenantId());
                }
                return message;
            }
        };
    }

    /**
     * 接收消息后恢复通用上下文。
     *
     * @param tenantProperties 租户配置
     * @return 接收后处理器
     */
    @Bean
    @ConditionalOnMissingBean(name = "rabbitContextReceivePostProcessor")
    public MessagePostProcessor rabbitContextReceivePostProcessor(TenantProperties tenantProperties) {
        return new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                Map<String, Object> headers = message.getMessageProperties().getHeaders();
                String dataSource = headerValue(headers, DataSourceUtil.DATA_SOURCE);
                if (StringUtils.isNotBlank(dataSource)) {
                    DynamicSourceTtl.push(dataSource);
                }
                String traceId = headerValue(headers, TRACE_ID);
                if (StringUtils.isNotBlank(traceId)) {
                    MDC.put(TRACE_ID, traceId);
                }
                if (tenantProperties.isEnabled()) {
                    String tenantId = tenantHeaderValue(headers, tenantProperties);
                    if (StringUtils.isNotBlank(tenantId)) {
                        TenantContextHolder.setTenantId(tenantId);
                    }
                }
                return message;
            }
        };
    }

    /**
     * 发送侧上下文透传增强。
     *
     * @param rabbitContextPublishPostProcessor 发送前处理器
     * @return RabbitTemplate 定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "rabbitContextTemplateCustomizer")
    public RabbitTemplateCustomizer rabbitContextTemplateCustomizer(
            @Qualifier("rabbitContextPublishPostProcessor")
            MessagePostProcessor rabbitContextPublishPostProcessor) {
        return rabbitTemplate -> rabbitTemplate.addBeforePublishPostProcessors(rabbitContextPublishPostProcessor);
    }

    /**
     * 消费完成后清理线程上下文，避免消费线程复用导致串上下文。
     *
     * @return 清理拦截器
     */
    @Bean
    @ConditionalOnMissingBean(name = "rabbitContextCleanupAdvice")
    public Advice rabbitContextCleanupAdvice() {
        return (MethodInterceptor) invocation -> {
            try {
                return invocation.proceed();
            } finally {
                DynamicSourceTtl.clear();
                TenantContextHolder.clear();
                MDC.remove(TRACE_ID);
            }
        };
    }

    /**
     * Simple 模式监听容器工厂。
     *
     * @param configurer                             Spring Boot 标准配置器
     * @param connectionFactory                      RabbitMQ 连接工厂
     * @param rabbitContextReceivePostProcessor      接收后处理器
     * @param rabbitContextCleanupAdvice             上下文清理拦截器
     * @param simpleMessageListenerContainerProvider 业务自定义容器增强
     * @return 监听容器工厂
     */
    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")
    @ConditionalOnProperty(name = "spring.rabbitmq.listener.type", havingValue = "simple", matchIfMissing = true)
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("rabbitContextReceivePostProcessor")
            MessagePostProcessor rabbitContextReceivePostProcessor,
            @Qualifier("rabbitContextCleanupAdvice")
            Advice rabbitContextCleanupAdvice,
            ObjectProvider<ContainerCustomizer<SimpleMessageListenerContainer>> simpleMessageListenerContainerProvider) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAfterReceivePostProcessors(rabbitContextReceivePostProcessor);
        appendAdvice(factory, rabbitContextCleanupAdvice);
        setContainerCustomizer(factory, simpleMessageListenerContainerProvider);
        return factory;
    }

    /**
     * Direct 模式监听容器工厂。
     *
     * @param configurer                             Spring Boot 标准配置器
     * @param connectionFactory                      RabbitMQ 连接工厂
     * @param rabbitContextReceivePostProcessor      接收后处理器
     * @param rabbitContextCleanupAdvice             上下文清理拦截器
     * @param directMessageListenerContainerProvider 业务自定义容器增强
     * @return 监听容器工厂
     */
    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")
    @ConditionalOnProperty(name = "spring.rabbitmq.listener.type", havingValue = "direct")
    public DirectRabbitListenerContainerFactory directRabbitListenerContainerFactory(
            DirectRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("rabbitContextReceivePostProcessor")
            MessagePostProcessor rabbitContextReceivePostProcessor,
            @Qualifier("rabbitContextCleanupAdvice")
            Advice rabbitContextCleanupAdvice,
            ObjectProvider<ContainerCustomizer<DirectMessageListenerContainer>> directMessageListenerContainerProvider) {
        DirectRabbitListenerContainerFactory factory = new DirectRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAfterReceivePostProcessors(rabbitContextReceivePostProcessor);
        appendAdvice(factory, rabbitContextCleanupAdvice);
        setContainerCustomizer(factory, directMessageListenerContainerProvider);
        return factory;
    }

    private static void appendAdvice(BaseRabbitListenerContainerFactory<?> factory, Advice advice) {
        Advice[] current = factory.getAdviceChain();
        if (current == null || current.length == 0) {
            factory.setAdviceChain(advice);
            return;
        }
        Advice[] merged = Arrays.copyOf(current, current.length + 1);
        merged[current.length] = advice;
        factory.setAdviceChain(merged);
    }

    private static <T extends AbstractMessageListenerContainer> void setContainerCustomizer(
            AbstractRabbitListenerContainerFactory<T> factory,
            ObjectProvider<ContainerCustomizer<T>> customizers) {
        List<ContainerCustomizer<T>> customizerList = customizers.orderedStream().toList();
        if (!customizerList.isEmpty()) {
            factory.setContainerCustomizer(container ->
                    customizerList.forEach(customizer -> customizer.configure(container)));
        }
    }

    private static void putHeader(Map<String, Object> headers, String name, String value) {
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(value)) {
            headers.put(name, value);
        }
    }

    private static String headerValue(Map<String, Object> headers, String name) {
        Object value = headers.get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if ("null".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private static String tenantHeaderValue(Map<String, Object> headers, TenantProperties tenantProperties) {
        if (tenantProperties.getHeaderNames() == null || tenantProperties.getHeaderNames().isEmpty()) {
            return null;
        }
        for (String headerName : tenantProperties.getHeaderNames()) {
            String tenantId = headerValue(headers, headerName);
            if (StringUtils.isNotBlank(tenantId)) {
                return tenantId;
            }
        }
        return null;
    }

    private static String tenantHeaderName(TenantProperties tenantProperties) {
        if (tenantProperties.getHeaderNames() == null || tenantProperties.getHeaderNames().isEmpty()) {
            return null;
        }
        return tenantProperties.getHeaderNames().get(0);
    }
}
