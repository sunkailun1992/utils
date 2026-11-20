package com.kellen.utils.mq;

/**
 * fleet 消息的上下文属性名。
 *
 * <p>发送时写成 RocketMQ 用户属性，消费钩子按它们还原线程上下文；名字只用小写字母与下划线，
 * 可以直接写进 SQL92 订阅表达式（泳道隔离就靠 {@link #LANE} 与 {@link #CANARY_TAG} 过滤）。</p>
 */
public final class MqHeaders {

    /** 发送时的租户ID。 */
    public static final String TENANT_ID = "fleet_tenant_id";

    /** 发送时的操作人用户ID，消费端只用于审计列。 */
    public static final String USER_ID = "fleet_user_id";

    /** 发送时的操作人部门ID，消费端只用于审计列。 */
    public static final String DEPT_ID = "fleet_dept_id";

    /** 发送时线程上的数据源标记；HTTP 请求通常没有，只有 Dubbo 透传过时才有。 */
    public static final String DATASOURCE = "fleet_datasource";

    /** 发送方实例自己的泳道（Nacos 注册元数据 {@code traffic.lane}），稳定实例为 {@code stable}。 */
    public static final String LANE = "fleet_lane";

    /** 发送方实例自己的灰度标签（Nacos 注册元数据 {@code canary.tag}），稳定实例没有。 */
    public static final String CANARY_TAG = "fleet_canary_tag";

    private MqHeaders() {
    }
}
