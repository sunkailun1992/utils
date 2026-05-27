package com.kellen.utils.convert;

/**
 * Object的工具类
 * @author 孙凯伦
 * @DateTime    2020/12/27  下午4:38
 * @email       376253703@qq.com
 * 
 */
public class ObjectUtils {

    /**
     * 判断是否基础类型
     * @author 孙凯伦
     * @DateTime    2020/7/22  9:52 上午
     * @email       376253703@qq.com
     * 
     * @param className:
     * @return      boolean
     */
    public static boolean isBaseType(Class className) {
        if (className.equals(String.class)) {
            return false;
        } else if (className.equals(Integer.class)) {
            return false;
        } else if (className.equals(int.class)) {
            return false;
        } else if (className.equals(Byte.class)) {
            return false;
        } else if (className.equals(byte.class)) {
            return false;
        } else if (className.equals(Long.class)) {
            return false;
        } else if (className.equals(long.class)) {
            return false;
        } else if (className.equals(Double.class)) {
            return false;
        } else if (className.equals(double.class)) {
            return false;
        } else if (className.equals(Float.class)) {
            return false;
        } else if (className.equals(float.class)) {
            return false;
        } else if (className.equals(char.class)) {
            return false;
        } else if (className.equals(Character.class)) {
            return false;
        } else if (className.equals(Short.class)) {
            return false;
        } else if (className.equals(short.class)) {
            return false;
        } else if (className.equals(Boolean.class)) {
            return false;
        } else if (className.equals(boolean.class)) {
            return false;
        } else if ("org.apache.catalina.connector.RequestFacade".equals(className.getName())) {
            return false;
        }
        return true;
    }

}
