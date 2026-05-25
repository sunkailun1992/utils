package com.kellen.utils.enumeration;

import com.kellen.utils.exception.ParameterNullException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * 系统编码枚举类
 * @author : ranyang
 * @date : 2021/04/06 14:25
 */
@Getter
@AllArgsConstructor
public enum AppCodeEnum {

    /**
     * NET_INS
     */
    NET_INS("net-ins", "工保前台保险公司"),

    /**
     * NET_USER
     */
    NET_USER("net-user", "工保网前台普通用户"),

    /**
     * NET_BACKEND
     */
    NET_BACKEND("net-backend", "工保网后端管理端"),

    /**
     * NET_AGENT
     */
    NET_AGENT("net-agent", "工保网前台经纪人"),

    /**
     * SCRM
     */
    SCRM("CRM", "工保网SCRM"),

    /**
     * NET_HX
     */
    NET_HX("net-hx", "工保网业务核心系统");

    /**
     * 平台编码
     */
    private String code;

    /**
     * 平台编码描述
     */
    private String desc;

    /**
     * 根据平台编码获取平台枚举类
     *
     * @param code: 平台编码
     * @return AppCodeEnum
     * @author sunx
     * @since 2021/3/19  4:35 下午
     */
    public static AppCodeEnum getAppCodeEnum(String code) {
        if(StringUtils.isBlank(code)){
            throw new ParameterNullException("平台编码不能为空!");
        }
        Optional<AppCodeEnum> codeEnum = Arrays.stream(values()).filter(x-> StringUtils.equalsIgnoreCase(code, x.getCode())).findFirst();
        return codeEnum.orElseThrow( ()-> new ParameterNullException("平台编码不存在!"));
    }
}