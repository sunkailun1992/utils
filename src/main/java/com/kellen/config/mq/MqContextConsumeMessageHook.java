package com.kellen.config.mq;

import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.context.TenantContextHolder;
import com.kellen.utils.mq.MqHeaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 消费钩子：处理前按消息属性设置租户、操作人与数据源标记，处理后恢复处理前的值（处理失败也恢复）。
 *
 * <p>属性缺的那项在处理期间是空的，不沿用线程上原有的值。还原的操作人只有用户ID、租户与部门，
 * 只用于审计列，不带权限与数据范围。只支持单条消费（与芋道的租户钩子一致）。</p>
 */
public class MqContextConsumeMessageHook implements ConsumeMessageHook {

    /** 每个消费线程上处理前的上下文；前后两个回调在同一线程上成对执行。 */
    private static final ThreadLocal<Deque<Previous>> PREVIOUS = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 创建消费钩子；没有状态，每个监听容器各挂一个。
     */
    public MqContextConsumeMessageHook() {
    }

    @Override
    public String hookName() {
        return "fleetMqContextConsumeMessageHook";
    }

    @Override
    public void consumeMessageBefore(ConsumeMessageContext context) {
        List<MessageExt> messages = context.getMsgList();
        int size = messages == null ? 0 : messages.size();
        if (size != 1) {
            throw new IllegalStateException("fleet 消费钩子只支持单条消费，本次收到 " + size + " 条");
        }
        PREVIOUS.get().push(Previous.capture());
        MessageExt message = messages.get(0);
        String tenantId = message.getUserProperty(MqHeaders.TENANT_ID);
        TenantContextHolder.clear();
        if (StringUtils.isNotBlank(tenantId)) {
            TenantContextHolder.setTenantId(tenantId);
        }
        String userId = message.getUserProperty(MqHeaders.USER_ID);
        if (StringUtils.isNotBlank(userId)) {
            UserContextHolder.set(new SecurityUser(userId, null, tenantId,
                    message.getUserProperty(MqHeaders.DEPT_ID), null, List.of(), List.of()));
        } else {
            UserContextHolder.clear();
        }
        String dataSource = message.getUserProperty(MqHeaders.DATASOURCE);
        DynamicSourceTtl.clear();
        if (StringUtils.isNotBlank(dataSource)) {
            DynamicSourceTtl.push(dataSource);
        }
    }

    @Override
    public void consumeMessageAfter(ConsumeMessageContext context) {
        Deque<Previous> stack = PREVIOUS.get();
        Previous previous = stack.poll();
        if (stack.isEmpty()) {
            PREVIOUS.remove();
        }
        if (previous != null) {
            previous.restore();
        }
    }

    /**
     * 处理前线程上的上下文。
     *
     * @param tenantId     租户ID
     * @param tenantIgnore 是否忽略租户
     * @param user         登录用户
     * @param dataSource   数据源标记
     */
    private record Previous(String tenantId, boolean tenantIgnore, SecurityUser user, String dataSource) {

        private static Previous capture() {
            return new Previous(TenantContextHolder.getTenantId(), TenantContextHolder.isIgnore(),
                    UserContextHolder.get(), DynamicSourceTtl.peek());
        }

        private void restore() {
            TenantContextHolder.clear();
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }
            if (tenantIgnore) {
                TenantContextHolder.ignore();
            }
            if (user == null) {
                UserContextHolder.clear();
            } else {
                UserContextHolder.set(user);
            }
            DynamicSourceTtl.clear();
            if (dataSource != null) {
                DynamicSourceTtl.push(dataSource);
            }
        }
    }
}
