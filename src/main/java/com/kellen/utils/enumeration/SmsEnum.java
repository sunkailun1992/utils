package com.kellen.utils.enumeration;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2019/4/9  10:25 AM
 * @email 376253703@qq.com
 * 
 * @explain
 */
public enum SmsEnum {
    //用户登录
    用户登录("用户登录","SMS_139795096"),
    //用户注册
    用户注册("用户注册","SMS_139795094"),
    //修改密码
    修改密码("修改密码","SMS_139795093"),
    //用户信息变更
    用户信息变更("用户信息变更","SMS_139795092"),
    //退保验证
    refundValidation("退保验证", "SMS_214517266"),
    //理赔验证
    compensationValidation("理赔验证", "SMS_217155196"),
    //投保咨询验证
    advisoryValidation("投保咨询验证", "SMS_139985825"),
    //订单通知
    orderNoticeNoNo("订单通知", "SMS_224351992"),
    //订单通知-订单号
    orderNotice("订单通知-订单号", "SMS_225910474"),
    //订单通过通知
    orderSuccessNotice("订单通过通知", "SMS_224347115"),
    //订单失败通知
    orderFailureNotice("订单失败通知", "SMS_224341970"),
    //渠道下单通知投保用户管家信息
    CHANNEL_ORDER_NOTICE("渠道下单通知投保用户管家信息", "SMS_232165633"),
    //电子签章短信校验
    电子签章短信校验("电子签章短信校验", "SMS_235476314"),
    //后补审核通知
    后补审核通知("后补审核通知", "SMS_236555383"),
    //后补审核支付通知
    后补审核支付通知("后补审核支付通知", "SMS_236560361"),
    //投保咨询
    投保咨询("投保咨询","SMS_238136476"),
    //经纪人咨询
    经纪人咨询("经纪人咨询","SMS_238161432"),
    //订单超时提醒
    订单超时提醒("订单超时提醒","SMS_238945969"),
    //用户注销模板
    用户注销模板("用户注销模板","SMS_248580335"),
    保险审核通过待支付("保险审核通过待支付","SMS_251775799"),
    渠道订单通知("渠道订单通知", "SMS_257832406"),
    出单完成通知("出单完成通知", "SMS_257887413"),
    工保云服预约("工保云服预约", "SMS_270240009"),
    投保单反担保通知("投保单反担保通知", "SMS_461300390"),
    未开标会员提醒("未开标会员提醒", "SMS_489685283"),
    ;

    private String state;
    private String value;

    SmsEnum(String state, String value) {
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
        for (SmsEnum d : SmsEnum.values()) {
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
        for (SmsEnum d : SmsEnum.values()) {
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
