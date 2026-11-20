package com.kellen.utils.mq;

import com.kellen.config.mq.MqInstanceLane;
import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * fleet 的 RocketMQ 发送工具，底层是 {@link RocketMQTemplate}，方法形状也照它来。
 *
 * <p>每次发送都按调用线程写上租户、操作人、数据源标记，以及本实例的泳道与灰度标签（见 {@link MqHeaders}）；
 * tag、key、延时、顺序通过 {@link MqSendOptions} 指定。业务代码经它发送，不直接用 RocketMQ 原生客户端。</p>
 */
@Slf4j
public class MqTemplate {

    /** 实际发送消息的 rocketmq-spring 模板。 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 本实例的泳道与灰度标签。 */
    private final MqInstanceLane lane;

    /**
     * 创建发送工具。
     *
     * @param rocketMQTemplate rocketmq-spring 模板
     * @param lane             本实例的泳道与灰度标签
     */
    public MqTemplate(RocketMQTemplate rocketMQTemplate, MqInstanceLane lane) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.lane = lane;
    }

    /**
     * 同步发送普通消息。
     *
     * @param topic   主题
     * @param payload 负载，按 JSON 序列化
     * @throws MqSendException 发送失败或 broker 没有返回 {@code SEND_OK}
     */
    public void send(String topic, Object payload) {
        send(topic, payload, MqSendOptions.none());
    }

    /**
     * 同步发送，带 tag、key、延时或顺序键。
     *
     * @param topic   主题
     * @param payload 负载，按 JSON 序列化
     * @param options 发送选项
     * @throws MqSendException 发送失败或 broker 没有返回 {@code SEND_OK}
     */
    public void send(String topic, Object payload, MqSendOptions options) {
        doSend(topic, payload, options, ContextSnapshot.capture());
    }

    /**
     * 当前事务提交后再发送；回滚不发送，没有事务时立即发送。
     *
     * @param topic   主题
     * @param payload 负载，按 JSON 序列化
     */
    public void sendAfterCommit(String topic, Object payload) {
        sendAfterCommit(topic, payload, MqSendOptions.none());
    }

    /**
     * 当前事务提交后再发送；回滚不发送，没有事务时立即发送。
     *
     * <p>上下文在调用时抓取（提交时请求线程上的租户等可能已经清掉）。发送失败只记一条错误日志
     * （目的地、key、租户，不含负载），不抛出：事务已经提交，调用方无法再回滚。</p>
     *
     * @param topic   主题
     * @param payload 负载，按 JSON 序列化
     * @param options 发送选项
     */
    public void sendAfterCommit(String topic, Object payload, MqSendOptions options) {
        ContextSnapshot snapshot = ContextSnapshot.capture();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendQuietly(topic, payload, options, snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendQuietly(topic, payload, options, snapshot);
            }
        });
    }

    private void sendQuietly(String topic, Object payload, MqSendOptions options, ContextSnapshot snapshot) {
        try {
            doSend(topic, payload, options, snapshot);
        } catch (RuntimeException ex) {
            MqSendOptions safe = options == null ? MqSendOptions.none() : options;
            // 只记异常类型：rocketmq-spring 的异常说明里带着整条消息，正文可能含个人信息。
            log.error("RocketMQ 消息在事务提交后发送失败，已放弃：destination={}, keys={}, tenant={}, error={}",
                    destination(topic, safe), safe.keys(), snapshot.tenantId(), ex.getClass().getName());
        }
    }

    private void doSend(String topic, Object payload, MqSendOptions options, ContextSnapshot snapshot) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("主题不能为空");
        }
        MqSendOptions safe = options == null ? MqSendOptions.none() : options;
        String destination = destination(topic, safe);
        Message<Object> message = message(payload, safe, snapshot);
        SendResult result;
        try {
            if (safe.orderKey() != null) {
                result = rocketMQTemplate.syncSendOrderly(destination, message, safe.orderKey());
            } else if (safe.delay() != null) {
                result = rocketMQTemplate.syncSendDelayTimeSeconds(destination, message, safe.delay().toSeconds());
            } else {
                result = rocketMQTemplate.syncSend(destination, message);
            }
        } catch (MessagingException ex) {
            throw new MqSendException("RocketMQ 发送失败：" + destination, ex);
        }
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new MqSendException("RocketMQ 发送没有得到确认：" + destination + "，状态 "
                    + (result == null ? null : result.getSendStatus()));
        }
    }

    private Message<Object> message(Object payload, MqSendOptions options, ContextSnapshot snapshot) {
        MessageBuilder<Object> builder = MessageBuilder.withPayload(payload);
        setIfPresent(builder, MqHeaders.TENANT_ID, snapshot.tenantId());
        setIfPresent(builder, MqHeaders.USER_ID, snapshot.userId());
        setIfPresent(builder, MqHeaders.DEPT_ID, snapshot.deptId());
        setIfPresent(builder, MqHeaders.DATASOURCE, snapshot.dataSource());
        builder.setHeader(MqHeaders.LANE, lane.lane());
        setIfPresent(builder, MqHeaders.CANARY_TAG, lane.canaryTag());
        if (!options.keys().isEmpty()) {
            builder.setHeader(RocketMQHeaders.KEYS, String.join(" ", options.keys()));
        }
        return builder.build();
    }

    private static void setIfPresent(MessageBuilder<Object> builder, String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            builder.setHeader(name, value);
        }
    }

    private static String destination(String topic, MqSendOptions options) {
        return StringUtils.isBlank(options.tag()) ? topic : topic + ":" + options.tag();
    }

    /**
     * 发送时抓取的线程上下文。
     *
     * @param tenantId   租户ID
     * @param userId     操作人用户ID
     * @param deptId     操作人部门ID
     * @param dataSource 数据源标记
     */
    private record ContextSnapshot(String tenantId, String userId, String deptId, String dataSource) {

        private static ContextSnapshot capture() {
            SecurityUser user = UserContextHolder.get();
            return new ContextSnapshot(TenantContextHolder.getTenantId(),
                    user == null ? null : user.getUserId(),
                    user == null ? null : user.getDeptId(),
                    DynamicSourceTtl.peek());
        }
    }
}
