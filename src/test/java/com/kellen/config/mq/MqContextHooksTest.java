package com.kellen.config.mq;

import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import com.kellen.utils.mq.MqHeaders;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收发钩子：发送时补上缺的上下文属性；消费时按属性还原租户、操作人与数据源，处理后恢复原值。
 */
class MqContextHooksTest {

    private final MqContextSendMessageHook sendHook = new MqContextSendMessageHook(new MqInstanceLane("canary", "kellen-local"));

    private final MqContextConsumeMessageHook consumeHook = new MqContextConsumeMessageHook();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        UserContextHolder.clear();
        DynamicSourceTtl.clear();
    }

    @Test
    void sendHookFillsMissingContextProperties() {
        TenantContextHolder.setTenantId("t1");
        UserContextHolder.set(new SecurityUser("u1", "user-name", "t1", "d1", null, List.of(), List.of()));
        DynamicSourceTtl.push("gray");
        Message message = new Message("github-dev-utils-test-topic", "body".getBytes(StandardCharsets.UTF_8));
        sendHook.sendMessageBefore(sendContext(message));
        assertThat(message.getUserProperty(MqHeaders.TENANT_ID)).isEqualTo("t1");
        assertThat(message.getUserProperty(MqHeaders.USER_ID)).isEqualTo("u1");
        assertThat(message.getUserProperty(MqHeaders.DEPT_ID)).isEqualTo("d1");
        assertThat(message.getUserProperty(MqHeaders.DATASOURCE)).isEqualTo("gray");
        assertThat(message.getUserProperty(MqHeaders.LANE)).isEqualTo("canary");
        assertThat(message.getUserProperty(MqHeaders.CANARY_TAG)).isEqualTo("kellen-local");
    }

    @Test
    void sendHookKeepsExistingProperties() {
        TenantContextHolder.setTenantId("t1");
        Message message = new Message("github-dev-utils-test-topic", "body".getBytes(StandardCharsets.UTF_8));
        message.putUserProperty(MqHeaders.TENANT_ID, "x");
        message.putUserProperty(MqHeaders.LANE, "stable");
        sendHook.sendMessageBefore(sendContext(message));
        assertThat(message.getUserProperty(MqHeaders.TENANT_ID)).isEqualTo("x");
        assertThat(message.getUserProperty(MqHeaders.LANE)).isEqualTo("stable");
    }

    @Test
    void consumeHookRestoresContextAndRestoresPrevious() {
        SecurityUser previousUser = new SecurityUser("pu", "previous", "pt", "pd", null, List.of(), List.of());
        TenantContextHolder.setTenantId("pt");
        UserContextHolder.set(previousUser);
        ConsumeMessageContext context = consumeContext(messageWith(Map.of(
                MqHeaders.TENANT_ID, "t1", MqHeaders.USER_ID, "u1", MqHeaders.DEPT_ID, "d1", MqHeaders.DATASOURCE, "gray")));

        consumeHook.consumeMessageBefore(context);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo("t1");
        assertThat(UserContextHolder.get().getUserId()).isEqualTo("u1");
        assertThat(UserContextHolder.get().getDeptId()).isEqualTo("d1");
        assertThat(UserContextHolder.get().getTenantId()).isEqualTo("t1");
        assertThat(DynamicSourceTtl.peek()).isEqualTo("gray");

        consumeHook.consumeMessageAfter(context);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo("pt");
        assertThat(UserContextHolder.get()).isSameAs(previousUser);
        assertThat(DynamicSourceTtl.peek()).isNull();
    }

    @Test
    void consumeHookRestoresAfterFailedConsume() {
        ConsumeMessageContext context = consumeContext(messageWith(Map.of(MqHeaders.TENANT_ID, "t1", MqHeaders.USER_ID, "u1")));
        consumeHook.consumeMessageBefore(context);
        context.setSuccess(false);
        context.setStatus("RECONSUME_LATER");
        consumeHook.consumeMessageAfter(context);
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void messageWithoutContextClearsThreadContext() {
        TenantContextHolder.setTenantId("stale");
        ConsumeMessageContext context = consumeContext(messageWith(Map.of()));
        consumeHook.consumeMessageBefore(context);
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(UserContextHolder.get()).isNull();
        consumeHook.consumeMessageAfter(context);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo("stale");
    }

    @Test
    void consumeHookRejectsBatch() {
        ConsumeMessageContext context = new ConsumeMessageContext();
        context.setMsgList(List.of(messageWith(Map.of()), messageWith(Map.of())));
        assertThatThrownBy(() -> consumeHook.consumeMessageBefore(context)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoredUserCarriesNoAuthorities() {
        ConsumeMessageContext context = consumeContext(messageWith(Map.of(MqHeaders.TENANT_ID, "t1", MqHeaders.USER_ID, "u1")));
        consumeHook.consumeMessageBefore(context);
        SecurityUser user = UserContextHolder.get();
        assertThat(user.getAuthorities()).isEmpty();
        assertThat(user.getDataScopeDeptIds()).isEmpty();
        assertThat(user.getDataScope()).isNull();
        consumeHook.consumeMessageAfter(context);
    }

    private static SendMessageContext sendContext(Message message) {
        SendMessageContext context = new SendMessageContext();
        context.setMessage(message);
        return context;
    }

    private static ConsumeMessageContext consumeContext(MessageExt message) {
        ConsumeMessageContext context = new ConsumeMessageContext();
        context.setMsgList(List.of(message));
        return context;
    }

    private static MessageExt messageWith(Map<String, String> properties) {
        MessageExt message = new MessageExt();
        message.setTopic("github-dev-utils-test-topic");
        message.setBody("{}".getBytes(StandardCharsets.UTF_8));
        properties.forEach(message::putUserProperty);
        return message;
    }
}
