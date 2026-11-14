package com.kellen.utils.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体类转换。
 *
 * <p>基于 Hutool {@link BeanUtil} 按同名属性拷贝，行为与原 Dozer 实现一致：源为 {@code null} 时返回 {@code null}；
 * 嵌套对象与集合深拷贝，不与源共享引用；拷入已有对象时源字段为 {@code null} 也覆盖目标字段。</p>
 *
 * @author 孙凯伦
 */
public class GeneralConvertor {

    /**
     * 拷贝选项：源值为 {@code null} 也覆盖目标；每个属性值在类型转换后再深拷贝一次。
     */
    private static final CopyOptions COPY_OPTIONS = CopyOptions.create()
            .setIgnoreNullValue(false)
            .setFieldValueEditor((fieldName, value) -> deepCopy(value));

    /**
     * List 实体类转换器，结果为按原顺序排列的 {@link ArrayList}。
     *
     * @param source 原数据
     * @param clz    转换类型
     * @param <T>    目标类型
     * @param <S>    源类型
     * @return 转换后的列表；源为 {@code null} 时返回 {@code null}
     */
    public static <T, S> List<T> convertor(List<S> source, Class<T> clz) {
        if (source == null) {
            return null;
        }
        List<T> targets = new ArrayList<>(source.size());
        for (S item : source) {
            targets.add(convertor(item, clz));
        }
        return targets;
    }

    /**
     * 实体类深度转换器。
     *
     * @param source 源对象
     * @param clz    转换类型
     * @param <T>    目标类型
     * @param <S>    源类型
     * @return 转换后的新对象；源为 {@code null} 时返回 {@code null}
     */
    public static <T, S> T convertor(S source, Class<T> clz) {
        if (source == null) {
            return null;
        }
        T target = ReflectUtil.newInstance(clz);
        BeanUtil.copyProperties(source, target, COPY_OPTIONS);
        return target;
    }

    /**
     * 把源对象属性深拷贝到已存在的目标对象。
     *
     * @param source 源对象
     * @param object 目标对象
     */
    public static void convertor(Object source, Object object) {
        BeanUtil.copyProperties(source, object, COPY_OPTIONS);
    }

    /**
     * 把源对象属性深拷贝到已存在的目标对象（泛型重载）。
     *
     * @param source 源对象
     * @param object 目标对象
     * @param <T>    源对象类型
     */
    public static <T> void copyConvertor(T source, Object object) {
        BeanUtil.copyProperties(source, object, COPY_OPTIONS);
    }

    /**
     * 深拷贝单个属性值：集合与 Map 生成新容器并逐个深拷贝元素，业务 Bean 生成新实例；
     * 字符串、数字、枚举、时间等 JDK 值类型原样返回。
     */
    private static Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            Collection<Object> copy = value instanceof Set ? new LinkedHashSet<>() : new ArrayList<>(collection.size());
            for (Object element : collection) {
                copy.add(deepCopy(element));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, element) -> copy.put(key, deepCopy(element)));
            return copy;
        }
        Class<?> type = value.getClass();
        if (!type.getName().startsWith("java.") && BeanUtil.isBean(type)) {
            Object copy = ReflectUtil.newInstanceIfPossible(type);
            if (copy != null) {
                BeanUtil.copyProperties(value, copy, COPY_OPTIONS);
                return copy;
            }
        }
        return value;
    }
}
