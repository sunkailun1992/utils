package com.kellen.utils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Stream集合处理工具类。
 *
 * @author 孙凯伦
 */
public class StreamUtils {

    /**
     * 提取集合中的指定字段为列表。
     *
     * @param list 原始集合
     * @param fun  字段提取函数
     * @param <T>  原始元素类型
     * @param <R>  目标字段类型
     * @return 字段列表
     */
    public static <T, R> List<R> fieldList(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList(); // 空集合返回空列表，避免调用方空指针。
        }

        return list.stream().map(fun).collect(Collectors.toList()); // 按提取函数映射字段并收集为列表。
    }

    /**
     * 提取集合中的指定字段为集合。
     *
     * @param list 原始集合
     * @param fun  字段提取函数
     * @param <T>  原始元素类型
     * @param <R>  目标字段类型
     * @return 字段集合
     */
    public static <T, R> Set<R> fieldSet(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Sets.newHashSet(); // 空集合返回空 Set，避免调用方空指针。
        }

        return list.stream().map(fun).collect(Collectors.toSet()); // 按提取函数映射字段并收集为 Set。
    }

    /**
     * 按指定字段构建 Map。
     *
     * @param list 原始集合
     * @param fun  Map key 提取函数
     * @param <T>  原始元素类型
     * @param <R>  Map key 类型
     * @return 字段到原始元素的映射
     */
    public static <T, R> Map<R, T> fieldMap(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap(); // 空集合返回空 Map，避免调用方空指针。
        }

        return list.stream().collect(Collectors.toMap(fun, e -> e)); // 使用字段值作为 key、原始对象作为 value。
    }

    /**
     * 按指定字段分组。
     *
     * @param list 原始集合
     * @param fun  分组字段提取函数
     * @param <T>  原始元素类型
     * @param <R>  分组 key 类型
     * @return 分组结果
     */
    public static <T, R> Map<R, List<T>> grouyBy(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap(); // 空集合返回空 Map，避免调用方空指针。
        }

        return list.stream().collect(Collectors.groupingBy(fun)); // 按字段提取函数执行分组。
    }

    /**
     * 按对象属性去重。
     *
     * @param keyExtractor 去重字段提取函数
     * @param <T>          元素类型
     * @return 可用于 Stream.filter 的去重断言
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>(); // 使用线程安全 Map 记录已经出现过的 key。
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null; // 首次出现返回 true，重复出现返回 false。
    }
}
