package com.kellen.config.mq;

import org.apache.rocketmq.spring.annotation.SelectorType;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 按本实例泳道算消费组名与 SQL92 订阅表达式（阿里 MSE 消息灰度的做法）。
 *
 * <ul>
 *     <li>稳定实例：消费组名不变，只收不带泳道或泳道为 {@code stable} 的消息；</li>
 *     <li>其他泳道：消费组名加 {@code -<泳道>-<标签>} 后缀（没有标签只加泳道），只收本泳道、本标签的消息。</li>
 * </ul>
 *
 * <p>原有的 tag 订阅合并成同一条 SQL；对已经处理过的名字与表达式再算一次结果不变。broker 须开 {@code enablePropertyFilter}。</p>
 */
public final class MqLaneSelector {

    /** 本实例的泳道与灰度标签。 */
    private final MqInstanceLane lane;

    /**
     * 创建选择器。
     *
     * @param lane 本实例的泳道与灰度标签
     */
    public MqLaneSelector(MqInstanceLane lane) {
        this.lane = lane;
    }

    /**
     * 本实例用的消费组名。
     *
     * @param original 配置里的消费组名
     * @return 稳定实例原样返回；其他泳道加后缀，已带后缀的不再加
     */
    public String consumerGroup(String original) {
        if (lane.stable()) {
            return original;
        }
        String suffix = "-" + lane.lane() + (lane.canaryTag() == null ? "" : "-" + lane.canaryTag());
        return original.endsWith(suffix) ? original : original + suffix;
    }

    /**
     * 本实例用的 SQL92 订阅表达式。
     *
     * @param originalType       监听器原有的选择器类型
     * @param originalExpression 监听器原有的表达式（tag 写法如 {@code a || b}，{@code *} 表示全部）
     * @return 合并了泳道条件的 SQL92 表达式
     */
    public String sqlExpression(SelectorType originalType, String originalExpression) {
        String condition = laneCondition();
        String expression = originalExpression == null ? "" : originalExpression.trim();
        if (originalType == SelectorType.SQL92) {
            if (expression.isEmpty() || expression.endsWith(condition)) {
                return expression.isEmpty() ? condition : expression;
            }
            return "(" + expression + ") AND " + condition;
        }
        if (expression.isEmpty() || "*".equals(expression)) {
            return condition;
        }
        String tags = Arrays.stream(expression.split("\\|\\|"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> "'" + tag.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        return "(TAGS IN (" + tags + ")) AND " + condition;
    }

    private String laneCondition() {
        if (lane.stable()) {
            return "(fleet_lane IS NULL OR fleet_lane = 'stable')";
        }
        if (lane.canaryTag() == null) {
            return "(fleet_lane = '" + lane.lane() + "')";
        }
        return "(fleet_lane = '" + lane.lane() + "' AND fleet_canary_tag = '" + lane.canaryTag() + "')";
    }
}
