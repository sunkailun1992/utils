package com.kellen.utils.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 长度枚举类
 * @author: sunx
 * @Date: 2021/04/06 14:25
 * @descript:
 */
@Getter
@AllArgsConstructor
@Slf4j
public enum LenEnum {

    /**
     * 身份证编码长度
     */
    ID_CARD_LEN(18, "身份证编码长度"),

    /**
     * 老身份证编码长度
     */
    OLD_ID_CARD_LEN(15, "老身份证编码长度"),

    /**
     * 格式YYYY-MM-DD日期长度
     */
    NORMAL_DATE_LEN(10, "格式YYYY-MM-DD日期长度");

    /**
     * 长度
     */
    private Integer len;

    /**
     * 描述
     */
    private String desc;
}
