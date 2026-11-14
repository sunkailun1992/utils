package com.kellen.utils.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link JsonUtil} 按 Hutool {@code JSONUtil} 的方法命名提供转换，默认用 Jackson 3 默认配置，
 * Spring 容器启动后改用 Spring Boot 配好的 {@link JsonMapper}。
 */
class JsonUtilTest {

    @Test
    void shouldUseSharedJackson3Mapper() {
        ObjectMapper mapper = JsonUtil.getJsonMapper();

        assertThat(mapper).isSameAs(JsonUtil.getJsonMapper());
        assertThat(mapper.getClass().getName()).startsWith("tools.jackson.");
    }

    @Test
    void shouldIgnoreUnknownProperties() {
        JsonFixture fixture = JsonUtil.toBean("{\"name\":\"营养干预\",\"score\":0.10,\"unknown\":\"ignored\"}", JsonFixture.class);

        assertThat(fixture.name).isEqualTo("营养干预");
        assertThat(fixture.score).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void shouldSerializeToJson() {
        JsonFixture fixture = new JsonFixture();
        fixture.name = "跌倒干预";

        assertThat(JsonUtil.toJsonStr(fixture)).contains("\"name\":\"跌倒干预\"");
    }

    @Test
    void shouldDeserializeTypedList() {
        List<JsonFixture> values = JsonUtil.toList("[{\"name\":\"structured-record\"}]", JsonFixture.class);

        assertThat(values).hasSize(1);
        assertThat(values.get(0).name).isEqualTo("structured-record");
    }

    @Test
    void shouldReturnNullForBlankInput() {
        assertThat(JsonUtil.toBean("", JsonFixture.class)).isNull();
        assertThat(JsonUtil.toList(null, JsonFixture.class)).isNull();
    }

    @Test
    void shouldWrapMalformedJsonWithoutLeakingPayload() {
        assertThatThrownBy(() -> JsonUtil.toBean("{\"name\":", JsonFixture.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JSON 反序列化失败");
    }

    @Test
    void usesSpringMapperAfterInit() {
        JsonMapper previous = JsonUtil.getJsonMapper();
        try {
            JsonUtil.init(JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build());

            assertThat(JsonUtil.toJsonStr(Map.of("a", 1))).contains("\n");
        } finally {
            JsonUtil.init(previous);
        }
    }

    static class JsonFixture {
        public String name;
        public BigDecimal score;
    }
}
