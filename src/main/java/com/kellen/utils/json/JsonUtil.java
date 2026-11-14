package com.kellen.utils.json;

import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;

/**
 * JSON 工具：方法命名对照 Hutool {@code JSONUtil}（{@code toJsonStr}、{@code toBean}、{@code toList}）。
 *
 * <p>Spring 容器启动后由 {@code JsonUtilAutoConfiguration} 调用 {@link #init(JsonMapper)}，改用 Spring Boot
 * 配好的 {@link JsonMapper}，与接口序列化规则一致；容器外使用 Jackson 3 默认配置，并忽略未知字段。</p>
 *
 * @author 孙凯伦
 */
public class JsonUtil {

    /**
     * 容器外的默认映射器：Jackson 3 默认配置，只额外忽略未知字段（与 Spring Boot 默认一致）。
     */
    private static final JsonMapper DEFAULT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * 当前使用的映射器。
     */
    private static volatile JsonMapper mapper = DEFAULT_MAPPER;

    /**
     * 改用指定映射器，通常由自动配置传入 Spring Boot 配好的 {@link JsonMapper}。
     *
     * @param jsonMapper 映射器
     */
    public static void init(JsonMapper jsonMapper) {
        mapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    /**
     * 获取当前使用的映射器。
     *
     * @return 映射器
     */
    public static JsonMapper getJsonMapper() {
        return mapper;
    }

    /**
     * JSON 转对象。
     *
     * @param value JSON 字符串
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 对象；输入为空时返回 {@code null}
     */
    public static <T> T toBean(String value, Class<T> type) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return mapper.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e); // 不带原文，避免日志泄露请求内容。
        }
    }

    /**
     * JSON 数组转列表。
     *
     * @param value JSON 字符串
     * @param type  元素类型
     * @param <T>   元素类型
     * @return 列表；输入为空时返回 {@code null}
     */
    public static <T> List<T> toList(String value, Class<T> type) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
            return mapper.readValue(value, listType);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 列表反序列化失败", e);
        }
    }

    /**
     * 对象转 JSON 字符串。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    public static String toJsonStr(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
