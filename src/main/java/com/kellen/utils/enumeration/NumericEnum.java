package com.kellen.utils.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数字枚举类
 * @author 孙凯伦
 * @Date 2021/04/06 14:25
 */
@Getter
@AllArgsConstructor
public enum NumericEnum {
    /**
     * 0
     */
    ZERO(0),
    /**
     * 1
     */
    ONE(1),
    /**
     * 2
     */
    TWO(2),
    /**
     * 3
     */
    THREE(3),
    /**
     * 4
     */
    FOUR(4),
    /**
     * 5
     */
    FIVE(5),
    /**
     * 6
     */
    SIX(6),
    /**
     * 7
     */
    SEVEN(7),
    /**
     * 8
     */
    EIGHT(8),
    /**
     * 9
     */
    NINE(9),
    /**
     * 10
     */
    TEN(10),
    ;

    /**
     * 值
     */
    private Integer value;
}