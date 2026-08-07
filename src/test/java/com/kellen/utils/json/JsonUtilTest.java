package com.kellen.utils.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 JsonUtil 迁移到 Jackson 3 后仍保持既有公共序列化契约。
 */
class JsonUtilTest {

    @Test
    void shouldUseSharedJackson3Mapper() {
        ObjectMapper mapper = JsonUtil.getJsonMapper();

        assertThat(mapper).isSameAs(JsonUtil.getJsonMapper());
        assertThat(mapper.getClass().getName()).startsWith("tools.jackson.");
    }

    @Test
    void shouldPreserveLegacyDeserializationRules() {
        JsonFixture fixture = JsonUtil.bean(
                "{'name':'营养干预','score':0.10,'unknown':'ignored'}", JsonFixture.class);

        assertThat(fixture.name).isEqualTo("营养干预");
        assertThat(fixture.score).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void shouldPreserveNullExclusionAndDateFormat() throws Exception {
        JsonFixture fixture = new JsonFixture();
        fixture.name = "跌倒干预";
        fixture.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-08-06 12:00:00");

        String json = JsonUtil.json(fixture);

        assertThat(json).contains("\"name\":\"跌倒干预\"");
        assertThat(json).contains("\"createdAt\":\"2026-08-06 12:00:00\"");
        assertThat(json).doesNotContain("optional");
    }

    @Test
    void shouldDeserializeTypedList() {
        List<JsonFixture> values = JsonUtil.list("[{'name':'structured-record'}]", JsonFixture.class);

        assertThat(values).hasSize(1);
        assertThat(values.get(0).name).isEqualTo("structured-record");
    }

    @Test
    void shouldKeepMaximumPrecisionForUntypedNumbers() {
        Map<?, ?> value = JsonUtil.bean("{'score':0.1234567890123456789}", Map.class);

        assertThat(value.get("score")).isEqualTo(new BigDecimal("0.1234567890123456789"));
    }

    @Test
    void shouldWrapMalformedJsonWithoutLeakingPayload() {
        assertThatThrownBy(() -> JsonUtil.bean("{'name':", JsonFixture.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JSON 反序列化失败");
    }

    static class JsonFixture {

        public String name;
        public BigDecimal score;
        public String optional;
        public Date createdAt;
    }
}
