package com.gb.utils.enumeration;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2019/4/9  10:25 AM
 * @email 376253703@qq.com
 * 
 * @explain
 */
public enum WxEnum {
    //课次消课
    courseEliminate("课次消课","UoVVrgxwWnJb6jYmSF37lp6yKst89BN1gxNf6Cxb1xY"),
    //课前提醒
    classNotice("课前提醒","lX7pRR2WYjLiMQy_QtdC97tXbKYa2cCFJfrR1dHSp-8"),
    //注册成功提醒
    registered("注册成功提醒","MEZXB2jsL2nC2nOMLO6YlxLBJycXoR1Jl6af2VBykJc"),
    //补课通知
    courseFill("补课通知","j36uBMau-KkiLE4XzpOx6HXEXevnroeAcvO2y_0o3Rg"),

    ;

    private String state;
    private String value;

    WxEnum(String state, String value) {
        this.state = state;
        this.value = value;
    }

    /**
     * 获得值
     *
     * @param state
     * @return
     */
    public static String getName(String state) {
        for (WxEnum d : WxEnum.values()) {
            if (d.getState().equals(state)) {
                return d.getValue();
            }
        }
        return null;
    }

    /**
     * 获得状态
     *
     * @param value
     * @return
     */
    public static String getState(String value) {
        for (WxEnum d : WxEnum.values()) {
            if (d.getValue().equals(value)) {
                return d.getState();
            }
        }
        return null;
    }


    /**
     * 获得枚举
     *
     * @param state
     * @return
     */
    public static WxEnum getSmsEnum(String state) {
        for (WxEnum d : WxEnum.values()) {
            if (d.getState().equals(state)) {
                return d;
            }
        }
        return null;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
