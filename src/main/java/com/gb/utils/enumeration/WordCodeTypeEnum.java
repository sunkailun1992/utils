package com.gb.utils.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: ranyang
 * @Date: 2021/04/06 14:25
 * @descript:
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("all")
public enum WordCodeTypeEnum {
    WORD_PROJECT(1, "项目"), WORD_ENTERPRISE(2, "企业");

    private int code;
    private String desc;
}
