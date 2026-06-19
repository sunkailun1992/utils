package com.kellen.utils.convert;

import java.util.Set;

/**
 * 对象类型工具类。
 *
 * @author 孙凯伦
 */
public final class ObjectUtils {

    /**
     * 视为叶子节点的基础类型集合：八种基本类型及其包装类、String，以及反射场景下不应继续递归的 Servlet RequestFacade。
     */
    private static final Set<String> BASE_TYPE_NAMES = Set.of(
            String.class.getName(),
            Integer.class.getName(), int.class.getName(),
            Byte.class.getName(), byte.class.getName(),
            Long.class.getName(), long.class.getName(),
            Double.class.getName(), double.class.getName(),
            Float.class.getName(), float.class.getName(),
            Character.class.getName(), char.class.getName(),
            Short.class.getName(), short.class.getName(),
            Boolean.class.getName(), boolean.class.getName(),
            "org.apache.catalina.connector.RequestFacade"
    );

    private ObjectUtils() {
    }

    /**
     * 判断给定类型是否为基础（叶子）类型。
     *
     * <p>语义修正：历史实现对基础类型返回 {@code false}（与方法名相反），现修正为基础类型返回 {@code true}、
     * 复杂对象返回 {@code false}，便于反射递归场景按字面语义判断是否继续展开字段。</p>
     *
     * @param className 待判断类型
     * @return 基础或既定叶子类型返回 true，复杂对象返回 false
     */
    public static boolean isBaseType(Class className) {
        if (className == null) {
            return false;
        }
        return BASE_TYPE_NAMES.contains(className.getName());
    }

}
