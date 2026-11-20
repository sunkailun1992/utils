package com.kellen.config.mq;

import org.apache.rocketmq.spring.annotation.SelectorType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 泳道隔离的消费组名与订阅表达式：稳定实例只收稳定消息，灰度实例只收本泳道、本标签的消息。
 */
class MqLaneSelectorTest {

    private static final String STABLE_CONDITION = "(fleet_lane IS NULL OR fleet_lane = 'stable')";

    private static final String CANARY_CONDITION = "(fleet_lane = 'canary' AND fleet_canary_tag = 'kellen-local')";

    private final MqLaneSelector stable = new MqLaneSelector(new MqInstanceLane("stable", null));

    private final MqLaneSelector canary = new MqLaneSelector(new MqInstanceLane("canary", "kellen-local"));

    @Test
    void stableKeepsGroupAndFiltersStableLane() {
        assertThat(stable.consumerGroup("g")).isEqualTo("g");
        assertThat(stable.sqlExpression(SelectorType.TAG, "*")).isEqualTo(STABLE_CONDITION);
    }

    @Test
    void canarySuffixesGroupAndFiltersOwnTag() {
        assertThat(canary.consumerGroup("g")).isEqualTo("g-canary-kellen-local");
        assertThat(canary.sqlExpression(SelectorType.TAG, "*")).isEqualTo(CANARY_CONDITION);
    }

    @Test
    void canaryWithoutTagFiltersLaneOnly() {
        MqLaneSelector lane = new MqLaneSelector(new MqInstanceLane("canary", null));
        assertThat(lane.consumerGroup("g")).isEqualTo("g-canary");
        assertThat(lane.sqlExpression(SelectorType.TAG, "*")).isEqualTo("(fleet_lane = 'canary')");
    }

    @Test
    void tagExpressionIsMergedIntoSql() {
        assertThat(stable.sqlExpression(SelectorType.TAG, "a || b"))
                .isEqualTo("(TAGS IN ('a', 'b')) AND " + STABLE_CONDITION);
    }

    @Test
    void starTagUsesLaneConditionOnly() {
        assertThat(canary.sqlExpression(SelectorType.TAG, " * ")).isEqualTo(CANARY_CONDITION);
        assertThat(canary.sqlExpression(SelectorType.TAG, "")).isEqualTo(CANARY_CONDITION);
    }

    @Test
    void sqlExpressionIsMergedIntoSql() {
        assertThat(canary.sqlExpression(SelectorType.SQL92, "amount > 10"))
                .isEqualTo("(amount > 10) AND " + CANARY_CONDITION);
    }

    @Test
    void laneSelectorIsIdempotent() {
        assertThat(canary.consumerGroup("g-canary-kellen-local")).isEqualTo("g-canary-kellen-local");
        String once = canary.sqlExpression(SelectorType.SQL92, "amount > 10");
        assertThat(canary.sqlExpression(SelectorType.SQL92, once)).isEqualTo(once);
        assertThat(stable.sqlExpression(SelectorType.SQL92, STABLE_CONDITION)).isEqualTo(STABLE_CONDITION);
    }
}
