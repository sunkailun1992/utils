package com.kellen.utils.enumeration;

import com.kellen.utils.exception.ParameterNullException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * 系统来源枚举类
 * @author 孙凯伦
 * @Date: 2021/04/06 14:25
 * @descript:
 */
@Getter
@AllArgsConstructor
@Slf4j
public enum SystemSourceEnum {

    /**
     * 工保网
     */
    GB_N("GONG_BAO_NET", "工保网"),

    /**
     * 工保盾
     */
    GB_D("GONG_BAO_DUN", "工保盾"),

    /**
     * 工保金
     */
    GB_J("GONG_BAO_JIN", "工保金"),

    /**
     * 工保通
     */
    GB_T("GONG_BAO_TON", "工保通"),

    /**
     * 工保网SCRM
     */
    GB_S("SCRM", "工保网SCRM"),

    /**
     * 小程序
     */
    GB_A("APPLET", "小程序"),

    /**
     * APP
     */
    APP("APP", "工保网APP"),

    /**
     * 一体化
     */
    GB_U("GONG_BAO_UNIFY", "一体化"),

    /**
     * 工保财务计算
     */
    GB_CWJS("GONG_BAO_CWJS", "工保财务计算"),

    /**
     * 安责
     */
    GB_AZ("GONG_BAO_AZ", "安责"),

    /**
     * 薪乐达
     */
    TRIPARTITE_XLD("TRIPARTITE_XLD", "薪乐达"),

    /**
     * H5
     */
    H5("H5", "H5"),

    /**
     * 公众号
     */
    OFFICIAL_ACCOUNT("OFFICIAL_ACCOUNT", "公众号"),

    /**
     * 电子保函
     */
    GONG_BAO_EGUARANTEE("GONG_BAO_EGUARANTEE", "电子保函"),

    /**
     * 会基
     */
    H_FOUNDATION("H_FOUNDATION", "会基"),

    ;

    /**
     * 系统来源编码
     */
    private String code;

    /**
     * 系统来源名称
     */
    private String desc;

    /**
     * 根据平台编码获取平台枚举类
     *
     * @param code: 系统来源编码
     * @return SystemSourceEnum
     * @author 孙凯伦
     * @since 2021/3/19  4:35 下午
     */
    public static SystemSourceEnum getSystemSourceEnum(String code) {
        if(StringUtils.isBlank(code)){
            throw new ParameterNullException("系统来源不能为空!");
        }
        Optional<SystemSourceEnum> codeEnum = Arrays.stream(values()).filter(x-> StringUtils.equals(StringUtils.upperCase(code), x.getCode())).findFirst();
        return codeEnum.orElseThrow( ()-> new ParameterNullException("系统来源不存在!"));
    }
}
