package com.kellen.utils.mq;

import com.kellen.config.mq.MqInstanceLane;
import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发送工具：按调用线程写上下文属性，tag、key、延时、顺序映射到 RocketMQTemplate 的对应方法，事务提交后才发送。
 */
@ExtendWith(OutputCaptureExtension.class)
class MqTemplateTest {

    /** 每个用例的主题。 */
    private static final String TOPIC = "github-dev-utils-test-topic";

    /** 模拟的 RocketMQTemplate，用来核对调用的方法与消息。 */
    private final RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        UserContextHolder.clear();
        DynamicSourceTtl.clear();
    }

    @Test
    void sendWritesContextHeaders() {
        stubSendOk();
        setContext("t1", "u1", "d1", "gray");
        template(canary()).send(TOPIC, "payload");
        Message<?> message = captureSyncSend(TOPIC);
        assertThat(message.getHeaders())
                .containsEntry(MqHeaders.TENANT_ID, "t1")
                .containsEntry(MqHeaders.USER_ID, "u1")
                .containsEntry(MqHeaders.DEPT_ID, "d1")
                .containsEntry(MqHeaders.DATASOURCE, "gray")
                .containsEntry(MqHeaders.LANE, "canary")
                .containsEntry(MqHeaders.CANARY_TAG, "kellen-local");
    }

    @Test
    void sendWithoutContextOmitsEmptyHeaders() {
        stubSendOk();
        template(stable()).send(TOPIC, "payload");
        Message<?> message = captureSyncSend(TOPIC);
        assertThat(message.getHeaders())
                .doesNotContainKeys(MqHeaders.TENANT_ID, MqHeaders.USER_ID, MqHeaders.DEPT_ID,
                        MqHeaders.DATASOURCE, MqHeaders.CANARY_TAG)
                .containsEntry(MqHeaders.LANE, "stable");
    }

    @Test
    void optionsMapToRocketMqCalls() {
        stubSendOk();
        MqTemplate template = template(stable());
        template.send(TOPIC, "a", MqSendOptions.none().withTag("a").withKeys("k1", "k2"));
        assertThat(captureSyncSend(TOPIC + ":a").getHeaders()).containsEntry(RocketMQHeaders.KEYS, "k1 k2");

        template.send(TOPIC, "b", MqSendOptions.none().withDelay(Duration.ofSeconds(5)));
        verify(rocketMQTemplate).syncSendDelayTimeSeconds(eq(TOPIC), any(Message.class), eq(5L));

        template.send(TOPIC, "c", MqSendOptions.none().withOrderKey("o1"));
        verify(rocketMQTemplate).syncSendOrderly(eq(TOPIC), any(Message.class), eq("o1"));
    }

    @Test
    void delayAndOrderKeyTogetherAreRejected() {
        assertThatThrownBy(() -> MqSendOptions.none().withOrderKey("o1").withDelay(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delayOutOfRangeIsRejected() {
        assertThatThrownBy(() -> MqSendOptions.none().withDelay(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqSendOptions.none().withDelay(Duration.ofDays(3).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendAfterCommitSendsOnlyAfterCommit() {
        stubSendOk();
        MqTemplate template = template(stable());
        new TransactionTemplate(new NoOpTransactionManager()).executeWithoutResult(status -> {
            setContext("t1", "u1", "d1", null);
            template.sendAfterCommit(TOPIC, "payload");
            clearContext();
            verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        });
        assertThat(captureSyncSend(TOPIC).getHeaders())
                .containsEntry(MqHeaders.TENANT_ID, "t1")
                .containsEntry(MqHeaders.USER_ID, "u1");
    }

    @Test
    void sendAfterCommitDropsOnRollback() {
        MqTemplate template = template(stable());
        new TransactionTemplate(new NoOpTransactionManager()).executeWithoutResult(status -> {
            template.sendAfterCommit(TOPIC, "payload");
            status.setRollbackOnly();
        });
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
    }

    @Test
    void sendAfterCommitWithoutTransactionSendsNow() {
        stubSendOk();
        template(stable()).sendAfterCommit(TOPIC, "payload");
        captureSyncSend(TOPIC);
    }

    @Test
    void sendAfterCommitFailureIsLoggedNotThrown(CapturedOutput output) {
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class)))
                .thenThrow(new MessagingException("syncSend failed, payload=secret-body-text"));
        MqTemplate template = template(stable());
        new TransactionTemplate(new NoOpTransactionManager()).executeWithoutResult(status ->
                template.sendAfterCommit(TOPIC, "secret-body-text", MqSendOptions.none().withKeys("event-1")));
        assertThat(output.getAll()).contains(TOPIC).contains("event-1").doesNotContain("secret-body-text");
    }

    @Test
    void sendFailureThrowsMqSendException() {
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class)))
                .thenThrow(new MessagingException("broker unavailable"));
        assertThatThrownBy(() -> template(stable()).send(TOPIC, "payload")).isInstanceOf(MqSendException.class);

        SendResult flushTimeout = new SendResult();
        flushTimeout.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        RocketMQTemplate other = mock(RocketMQTemplate.class);
        when(other.syncSend(anyString(), any(Message.class))).thenReturn(flushTimeout);
        assertThatThrownBy(() -> new MqTemplate(other, stable()).send(TOPIC, "payload"))
                .isInstanceOf(MqSendException.class);
    }

    @Test
    void instanceLaneDefaultsToStable() {
        assertThat(MqInstanceLane.resolve(new MockEnvironment())).isEqualTo(new MqInstanceLane("stable", null));
        MockEnvironment canary = new MockEnvironment()
                .withProperty("spring.cloud.nacos.discovery.metadata[traffic.lane]", "canary")
                .withProperty("spring.cloud.nacos.discovery.metadata[canary.tag]", "kellen-local");
        assertThat(MqInstanceLane.resolve(canary)).isEqualTo(new MqInstanceLane("canary", "kellen-local"));
    }

    @Test
    void invalidLaneIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.cloud.nacos.discovery.metadata[traffic.lane]", "canary' OR '1'='1");
        assertThatThrownBy(() -> MqInstanceLane.resolve(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("traffic.lane");
    }

    private MqTemplate template(MqInstanceLane lane) {
        return new MqTemplate(rocketMQTemplate, lane);
    }

    private static MqInstanceLane stable() {
        return new MqInstanceLane("stable", null);
    }

    private static MqInstanceLane canary() {
        return new MqInstanceLane("canary", "kellen-local");
    }

    private static void setContext(String tenant, String user, String dept, String dataSource) {
        TenantContextHolder.setTenantId(tenant);
        UserContextHolder.set(new SecurityUser(user, "user-name", tenant, dept, null, List.of(), List.of()));
        if (dataSource != null) {
            DynamicSourceTtl.push(dataSource);
        }
    }

    private void stubSendOk() {
        SendResult ok = new SendResult();
        ok.setSendStatus(SendStatus.SEND_OK);
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(ok);
        when(rocketMQTemplate.syncSendDelayTimeSeconds(anyString(), any(Message.class), anyLong())).thenReturn(ok);
        when(rocketMQTemplate.syncSendOrderly(anyString(), any(Message.class), anyString())).thenReturn(ok);
    }

    @SuppressWarnings("unchecked")
    private Message<?> captureSyncSend(String destination) {
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass((Class) Message.class);
        verify(rocketMQTemplate).syncSend(eq(destination), captor.capture());
        return captor.getValue();
    }

    /** 不连库的事务管理器：只为驱动 Spring 的事务同步回调。 */
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
