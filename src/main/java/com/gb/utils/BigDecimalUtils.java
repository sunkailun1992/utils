package com.gb.utils;

import java.math.BigDecimal;

/**
 * 数字精度工具类
 *
 * @author sunkailun
 * @DateTime 2020/12/27  下午4:40
 * @email 376253703@qq.com
 * 
 */
public class BigDecimalUtils {
    /**
     * 大于0表示前一个数据比后一个数据大， 0表示相等，小于0表示第一个数据小于第二个数据
     *
     * @param value
     * @param compare
     * @return
     * @return BigDecimal        返回类型
     * @Title: returnBig    方法名
     * @Description: 执行内容, 比较大小, 返回大值
     */
    public static BigDecimal returnBig(BigDecimal value, BigDecimal compare) {
        Integer integer = value.compareTo(compare);
        if (integer == -1) {
            return compare;
        } else if (integer == 1) {
            return value;
        } else {
            return value;
        }
    }

    /**
     * 大于0表示前一个数据比后一个数据大， 0表示相等，小于0表示第一个数据小于第二个数据
     *
     * @param value
     * @param compare
     * @return
     * @return BigDecimal        返回类型
     * @Title: returnSmall    方法名
     * @Description: 执行内容:比较大小,返回小值
     */
    public static BigDecimal returnSmall(BigDecimal value, BigDecimal compare) {
        Integer integer = value.compareTo(compare);
        if (integer == -1) {
            return value;
        } else if (integer == 1) {
            return compare;
        } else {
            return value;
        }
    }

    /**
     * @param value
     * @param compare
     * @return
     * @return boolean        返回类型
     * @Title: equal    方法名
     * @Description: 执行内容:判断是否相等
     */
    public static boolean equal(BigDecimal value, BigDecimal compare) {
        Integer integer = value.compareTo(compare);
        if (integer == 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param value
     * @param compare
     * @return
     * @return boolean        返回类型
     * @Title: equal    方法名
     * @Description: 执行内容:判断是否小于等于
     */
    public static boolean smallEqual(BigDecimal value, BigDecimal compare) {
        Integer integer = value.compareTo(compare);
        if (integer == 1) {
            return true;
        } else if (integer == 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param value
     * @param compare
     * @return
     * @return boolean        返回类型
     * @Title: equal    方法名
     * @Description: 执行内容:判断是否大于
     */
    public static boolean bigEqual(BigDecimal value, BigDecimal compare) {
        Integer integer = value.compareTo(compare);
        if (integer == -1) {
            return true;
        } else if (integer == 0) {
            return true;
        } else {
            return false;
        }
    }

}
