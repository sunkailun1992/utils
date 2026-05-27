package com.kellen.utils.enumeration;

/**
 * 用户通知短信模板枚举。
 *
 * @author 孙凯伦
 */
public enum SmsEnum {
    /**
     * 用户登录通知模板。
     */
    用户登录("用户登录","SMS_139795096"),

    /**
     * 用户注册通知模板。
     */
    用户注册("用户注册","SMS_139795094"),

    /**
     * 修改密码通知模板。
     */
    修改密码("修改密码","SMS_139795093");

    /**
     * 模板业务名称。
     */
    private String state;

    /**
     * 阿里云短信模板编码。
     */
    private String value;

    /**
     * 构造用户通知短信模板枚举。
     *
     * @param state 模板业务名称
     * @param value 阿里云短信模板编码
     */
    SmsEnum(String state, String value) {
        this.state = state;
        this.value = value;
    }

    /**
     * 根据模板业务名称获取阿里云短信模板编码。
     *
     * @param state 模板业务名称
     * @return 阿里云短信模板编码
     */
    public static String getName(String state) {
        for (SmsEnum d : SmsEnum.values()) {
            if (d.getState().equals(state)) {
                return d.getValue();
            }
        }
        return null;
    }

    /**
     * 根据阿里云短信模板编码获取模板业务名称。
     *
     * @param value 阿里云短信模板编码
     * @return 模板业务名称
     */
    public static String getState(String value) {
        for (SmsEnum d : SmsEnum.values()) {
            if (d.getValue().equals(value)) {
                return d.getState();
            }
        }
        return null;
    }


    /**
     * 根据模板业务名称获取枚举。
     *
     * @param state 模板业务名称
     * @return 用户通知短信模板枚举
     */
    public static SmsEnum getSmsEnum(String state) {
        for (SmsEnum d : SmsEnum.values()) {
            if (d.getState().equals(state)) {
                return d;
            }
        }
        return null;
    }

    public String getState() {
        return state;
    }

    /**
     * 设置模板业务名称。
     *
     * @param state 模板业务名称
     */
    public void setState(String state) {
        this.state = state;
    }

    public String getValue() {
        return value;
    }

    /**
     * 设置阿里云短信模板编码。
     *
     * @param value 阿里云短信模板编码
     */
    public void setValue(String value) {
        this.value = value;
    }
}
