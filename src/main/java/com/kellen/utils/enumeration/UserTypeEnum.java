package com.kellen.utils.enumeration;

import com.kellen.utils.exception.BusinessException;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author 孙凯伦
 * @Date: 2021/11/09 09:39
 * @descript:
 */
@SuppressWarnings("all")
public enum UserTypeEnum {
    前端用户("average_user"),
    管理用户("management_user"),
    保险公司("insurance_company"),
    经纪人("213"),
    服务管家主管("231"),
    互联网管家主管("229"),
    商务管家主管("230"),
    特别业务管家("234"),
    特殊经纪人("237"),
    渠道经纪人("238"),
    业务管家("214"),
    业务助理管家("215"),
    互联网管家("216"),
    商务管家("227"),
    业务管家主管("228"),
    用户咨询("240"),
    经纪人咨询("241"),
    管理员("235"),
    数广团队长("100242"),
    企业顾问服务("100250");
    private String typeCode;
    UserTypeEnum(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeCode() {
        return typeCode;
    }


    public static UserTypeEnum getTypeByCode(String code) {
        Optional<UserTypeEnum> value = Arrays.stream(UserTypeEnum.values()).filter(x -> x.getTypeCode().equals(code)).findFirst();
        if (value.isPresent()) {
            return value.get();
        }
        throw new BusinessException("未知类型" + code);
    }
}
