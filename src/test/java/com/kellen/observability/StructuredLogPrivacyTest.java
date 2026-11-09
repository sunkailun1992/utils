package com.kellen.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.structured.StructuredLogFormatterFactory;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 运行 Boot 的真实 ECS formatter，验证共享配置不会导出任意 MDC 或原始错误。 */
class StructuredLogPrivacyTest {
    @Test
    void templateDisablesUntrustedContextBeforeFleetCustomizer() throws Exception {
        String json = format(Map.of("spring.application.name", "fixture-java",
                "spring.application.version", "1.2.3"), true);
        // 根制品不依赖轻量模块：模板先关闭 Boot 的自由 MDC/KVP 合并；
        // 受限 trace/事件字段由轻量模块 SPI 提供，并在该模块真实 encoder 回归中验证。
        assertThat(json).contains("WARN");
        assertThat(json).doesNotContain("trace_id", "span_id", "SYNTHETIC_PRIVATE", "private_field");
        assertThat(service(json)).containsEntry("name", "fixture-java")
                .containsEntry("version", "1.2.3").containsEntry("environment", "github-dev");
    }

    @Test
    void missingVersionIsExplicitUnknownAndStartupLogNeedsNoTrace() throws Exception {
        String json = format(Map.of("spring.application.name", "fixture-startup"), false);
        assertThat(service(json)).containsEntry("name", "fixture-startup")
                .containsEntry("version", "unknown").containsEntry("environment", "github-dev");
        assertThat(json).doesNotContain("trace_id", "span_id", "SYNTHETIC_PRIVATE");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> service(String json) throws Exception {
        return (Map<String, Object>) JsonMapper.builder().build().readValue(json, Map.class).get("service");
    }

    private String format(Map<String, Object> properties, boolean traced) throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("fixture", properties));
        var resource = new FileSystemResource("ops/github-dev/nacos/DEFAULT_GROUP/logging.yml");
        new YamlPropertySourceLoader().load("logging-template", resource)
                .forEach(environment.getPropertySources()::addFirst);
        var factory = new StructuredLogFormatterFactory<ILoggingEvent>(ILoggingEvent.class, environment,
                parameters -> parameters.add(ThrowableProxyConverter.class, new ThrowableProxyConverter()),
                formats -> { });
        var formatter = factory.get("org.springframework.boot.logging.logback.ElasticCommonSchemaStructuredLogFormatter");
        var event = new LoggingEvent();
        event.setLevel(Level.WARN);
        event.setLoggerName("fixture.logger");
        event.setThreadName("fixture");
        event.setTimeStamp(1_700_000_000_000L);
        event.setMessage("SYNTHETIC_PRIVATE_MESSAGE");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("SYNTHETIC_PRIVATE_EXCEPTION")));
        event.setMDCPropertyMap(traced ? Map.of("trace_id", "0123456789abcdef0123456789abcdef",
                "span_id", "0123456789abcdef", "private_field", "SYNTHETIC_PRIVATE_MDC")
                : Map.of("private_field", "SYNTHETIC_PRIVATE_MDC"));
        return formatter.format(event);
    }
}
