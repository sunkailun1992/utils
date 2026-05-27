package com.kellen.bean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @author 孙凯伦
 * 
 * @email: 376253703@qq.com
 * @name:
 * @description: TODO 微信实体类
 * @date: 2022/1/6 3:36 PM
 */
@Configuration
public class WeChat {
    /**
     * 公众账号ID
     */
    public static String appid;

    /**
     * 商户密钥
     */
    public static String secret;


    @Value("${gongbao.wx.appid}")
    public void setAppid(String appid) {
        WeChat.appid = appid;
    }

    @Value("${gongbao.wx.secret}")
    public void setSecret(String secret) {
        WeChat.secret = secret;
    }
}
