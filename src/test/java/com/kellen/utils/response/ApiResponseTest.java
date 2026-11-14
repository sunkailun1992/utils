package com.kellen.utils.response;

import com.kellen.utils.enumeration.ReturnCode;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证统一返回对象：没有与 errorMessage 重复的 msg，带当前链路的 traceId，时间固定按 GMT+8，失败工厂方法可用于有类型的接口。
 */
class ApiResponseTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN = "00f067aa0ba902b7";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void failureHasNoMsgField() {
        JsonNode json = json(ApiResponse.fail(ReturnCode.USER_PASSWORD_ERROR, "用户名或密码错误"));

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("code").asString()).isEqualTo("A0210");
        assertThat(json.get("errorMessage").asString()).isEqualTo("用户名或密码错误");
        assertThat(json.has("msg")).isFalse();
    }

    @Test
    void successHasNoErrorMessageOrMsg() {
        JsonNode json = json(ApiResponse.success("x"));

        assertThat(json.get("code").asString()).isEqualTo("0");
        assertThat(json.get("data").asString()).isEqualTo("x");
        assertThat(json.has("errorMessage")).isFalse();
        assertThat(json.has("msg")).isFalse();
    }

    @Test
    void traceIdComesFromCurrentSpan() {
        Span span = Span.wrap(SpanContext.create(TRACE, SPAN, TraceFlags.getSampled(), TraceState.getDefault()));
        ApiResponse<Void> response;
        try (Scope ignored = span.makeCurrent()) {
            response = ApiResponse.fail(ReturnCode.SYSTEM_EXECUTION_ERROR);
        }

        assertThat(response.getTraceId()).isEqualTo(TRACE);
        assertThat(json(response).get("traceId").asString()).isEqualTo(TRACE);
    }

    @Test
    void traceIdOmittedWithoutSpan() {
        assertThat(json(ApiResponse.success()).has("traceId")).isFalse();
    }

    @Test
    void timestampIsGmt8RegardlessOfJvmZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            String timestamp = json(ApiResponse.success()).get("timestamp").asString();

            OffsetDateTime parsed = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atOffset(ZoneOffset.ofHours(8));
            assertThat(Duration.between(parsed, OffsetDateTime.now()).abs()).isLessThan(Duration.ofSeconds(60));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void failCanBeReturnedFromTypedEndpoint() {
        ApiResponse<String> response = ApiResponse.fail(ReturnCode.SYSTEM_EXECUTION_ERROR);

        assertThat(response.getData()).isNull();
        assertThat(response.getSuccess()).isFalse();
    }

    private static JsonNode json(ApiResponse<?> response) {
        return MAPPER.readTree(MAPPER.writeValueAsString(response));
    }
}
