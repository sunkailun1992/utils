package com.kellen.utils.enumeration;

import com.kellen.utils.exception.ParameterNullException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @date 2018/4/12  下午5:14
 */
@Getter
@AllArgsConstructor
public enum SourceValueEnum {

    /**
     * 前台注册
     */
    前台注册("GBW_FRONT", "前台注册"),

    /**
     * 后台注册
     */
    后台注册("GBW_BAC", "后台注册"),

    /**
     * 系统初始化
     */
    系统初始化("SYS_INIT", "系统初始化"),

    /**
     * 客户投保
     */
    客户投保("CUS_INSURANCE", "客户投保"),

    /**
     * 静默注册
     */
    静默注册("SILENT_REGISTER", "静默注册"),

    /**
     * 信息收集表单
     */
    信息收集表单("INFO_COLLECTION_FORM", "信息收集表单");

    /**
     * 来源值码值
     */
    private String code;

    /**
     * 来源值名称
     */
    private String desc;

    /**
     * 根据来源值编码获取来源值枚举类
     *
     * @param code: 来源值编码
     * @return SourceValueEnum
     * @author 孙凯伦
     * @since 2021/3/19  4:35 下午
     */
    public static SourceValueEnum getAppCodeEnum(String code) {
        if(StringUtils.isBlank(code)){
            throw new ParameterNullException("来源值编码不能为空!");
        }
        Optional<SourceValueEnum> codeEnum = Arrays.stream(values()).filter(x-> StringUtils.equals(StringUtils.upperCase(code), x.getCode())).findFirst();
        return codeEnum.orElseThrow( ()-> new ParameterNullException("来源值编码不存在!"));
    }

}
