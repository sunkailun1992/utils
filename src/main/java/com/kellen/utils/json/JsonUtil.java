package com.kellen.utils.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 基于 Spring Boot 4 默认 Jackson 3 实现的 JSON 转换工具。
 *
 * <p>共享 mapper 在类初始化时完成全部配置，消费者不得在运行期间修改其序列化规则，
 * 避免多个线程和多个业务服务之间出现不可预测的 JSON 行为。</p>
 */
public class JsonUtil {

    /**
     * 公共 JSON mapper，兼容历史单引号、未知字段忽略、空值不输出和日期格式规则。
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .changeDefaultPropertyInclusion(inclusion ->
                    inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
            .findAndAddModules()
            .build();

    /**
     * 返回公共 Jackson 3 mapper。
     *
     * <p>该对象由所有调用方共享，只允许读写 JSON，不应重新配置或注册模块。</p>
     *
     * @return 已完成公共规则配置的共享 mapper
     */
    public static ObjectMapper getJsonMapper() {
        return MAPPER;
    }

    /**
     * 将 JSON 文本转换为指定类型。
     *
     * @param value JSON 文本；null 或空字符串返回 null
     * @param basicClass 目标类型
     * @param <T> 目标类型
     * @return 转换结果
     * @throws IllegalStateException JSON 格式错误或类型不兼容时抛出
     */
    public static <T> T bean(String value, Class<T> basicClass) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return MAPPER.readValue(value, basicClass);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    /**
     * 将 JSON 数组文本转换为指定元素类型的列表。
     *
     * @param value JSON 数组文本；null 或空字符串返回 null
     * @param classType 列表元素类型
     * @param <T> 列表元素类型
     * @return 转换后的列表
     * @throws IllegalStateException JSON 格式错误或元素类型不兼容时抛出
     */
    public static <T> List<T> list(String value, Class<T> classType) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructCollectionType(List.class, classType);
            return MAPPER.readValue(value, javaType);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 列表反序列化失败", e);
        }
    }

    /**
     * 将对象序列化为 JSON 文本。
     *
     * @param value 待序列化对象
     * @return JSON 文本
     * @throws IllegalStateException 对象无法序列化时抛出
     */
    public static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

}
