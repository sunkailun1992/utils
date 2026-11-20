package com.kellen.config.mq;

import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import com.kellen.utils.mq.MqHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.common.message.Message;

/**
 * 发送钩子：消息上缺哪项上下文属性，就按当前线程补上（已有的不覆盖）。
 *
 * <p>{@code MqTemplate} 发送时已按调用线程写好属性；这里给业务直接用 {@code RocketMQTemplate} 同步发送兜底。
 * 异步发送在客户端线程里执行，线程上没有上下文，所以异步发送要经 {@code MqTemplate}。</p>
 */
public class MqContextSendMessageHook implements SendMessageHook {

    /** 本实例的泳道与灰度标签。 */
    private final MqInstanceLane lane;

    /**
     * 创建发送钩子。
     *
     * @param lane 本实例的泳道与灰度标签
     */
    public MqContextSendMessageHook(MqInstanceLane lane) {
        this.lane = lane;
    }

    @Override
    public String hookName() {
        return "fleetMqContextSendMessageHook";
    }

    @Override
    public void sendMessageBefore(SendMessageContext context) {
        Message message = context.getMessage();
        if (message == null) {
            return;
        }
        putIfAbsent(message, MqHeaders.TENANT_ID, TenantContextHolder.getTenantId());
        SecurityUser user = UserContextHolder.get();
        if (user != null) {
            putIfAbsent(message, MqHeaders.USER_ID, user.getUserId());
            putIfAbsent(message, MqHeaders.DEPT_ID, user.getDeptId());
        }
        putIfAbsent(message, MqHeaders.DATASOURCE, DynamicSourceTtl.peek());
        putIfAbsent(message, MqHeaders.LANE, lane.lane());
        putIfAbsent(message, MqHeaders.CANARY_TAG, lane.canaryTag());
    }

    @Override
    public void sendMessageAfter(SendMessageContext context) {
        // 发送后不需要处理。
    }

    private static void putIfAbsent(Message message, String name, String value) {
        if (StringUtils.isNotBlank(value) && message.getUserProperty(name) == null) {
            message.putUserProperty(name, value);
        }
    }
}
