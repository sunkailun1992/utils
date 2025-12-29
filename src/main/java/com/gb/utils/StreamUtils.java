package com.gb.utils;

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
 * @author syrobin
 * @date 2022/8/19
 */
public class StreamUtils {

    /**
     * 获取字段列表
     *
     * @param list
     * @param fun
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> List<R> fieldList(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }

        return list.stream().map(fun).collect(Collectors.toList());
    }

    /**
     * 获取字段set
     *
     * @param list
     * @param fun
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Set<R> fieldSet(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Sets.newHashSet();
        }

        return list.stream().map(fun).collect(Collectors.toSet());
    }

    /**
     * 获取字段列表
     *
     * @param list
     * @param fun
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Map<R, T> fieldMap(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.toMap(fun, e -> e));
    }

    /**
     * 分组
     *
     * @param list
     * @param fun
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Map<R, List<T>> grouyBy(Collection<T> list, Function<T, R> fun) {
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }

        return list.stream().collect(Collectors.groupingBy(fun));
    }

    /**
     * 按照属性对对象列表进行去重
     * @param keyExtractor
     * @return
     * @param <T>
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }


    public static void main(String[] args) {
        List<String> list = Lists.newArrayList("1", "2", "3", "4", "5");
        List<String> list1 = fieldList(list, e -> e);
        System.out.println(list1);
        Set<String> set = fieldSet(list, e -> e);
        System.out.println(set);
        Map<String, String> map = fieldMap(list, e -> e);
        System.out.println(map);
        Map<String, List<String>> map1 = grouyBy(list, e -> e);
        System.out.println(map1);

        List<String> keyList = Lists.newArrayList("1", "2", "3", "4", "5","1","2","3","4","5");
        List<String> distinctByKeyList = keyList.stream().filter(distinctByKey(e -> e)).collect(Collectors.toList());
        System.out.println(distinctByKeyList);
    }
}
