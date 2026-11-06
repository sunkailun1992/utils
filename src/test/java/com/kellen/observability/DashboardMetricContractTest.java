package com.kellen.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 用真实 Prometheus scrape 验证 Agent Timer 终态与看板 selector 的消费契约。 */
class DashboardMetricContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void agentTimerCountsAllTerminalOutcomesSelectedByDashboard() throws Exception {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            registry.config().commonTags("application", "ai-agent");
            for (String outcome : List.of("success", "failure", "cancelled")) {
                // 与 AiModelCallObserver 的真实 instrument/tag 契约一致，不把 Timer 当 Counter。
                Timer.builder("ai.agent.model.call").tag("outcome", outcome)
                        .register(registry).record(Duration.ofMillis(5));
            }
            Map<String, Object> dashboard = JsonMapper.builder().build().readValue(
                    Path.of("ops/github-dev/observability/grafana/dashboards/fleet.json").toFile(), Map.class);
            List<Map<String, Object>> panels = (List<Map<String, Object>>) dashboard.get("panels");
            Map<String, Object> panel = panels.stream().filter(item -> item.get("id").equals(8)).findFirst().orElseThrow();
            List<Map<String, Object>> targets = (List<Map<String, Object>>) panel.get("targets");
            String expression = (String) targets.get(0).get("expr");
            var selector = Pattern.compile("__name__=~\"([^\"]+)\"").matcher(expression);
            assertThat(selector.find()).isTrue();
            assertThat("ai_agent_model_call_seconds_count").matches(selector.group(1));
            assertThat(expression).contains("sum by(application,outcome)");
            String scrape = registry.scrape();
            for (String outcome : List.of("success", "failure", "cancelled")) {
                assertThat(scrape.lines().filter(line -> line.startsWith("ai_agent_model_call_seconds_count{")
                        && line.contains("application=\"ai-agent\"")
                        && line.contains("outcome=\"" + outcome + "\""))).hasSize(1);
            }
        } finally {
            registry.close();
        }
    }
}
