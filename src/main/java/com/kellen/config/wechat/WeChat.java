package com.kellen.config.wechat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 微信配置持有器。
 *
 * @author 孙凯伦
 */
@Configuration
public class WeChat {

    /**
     * 公众账号ID。
     */
    public static String appid;

    /**
     * 公众账号密钥。
     */
    public static String secret;


    /**
     * 注入公众账号ID。
     *
     * @param appid 公众账号ID
     */
    @Value("${gongbao.wx.appid}")
    public void setAppid(String appid) {
        WeChat.appid = appid; // 保持历史静态字段读取方式。
    }

    /**
     * 注入公众账号密钥。
     *
     * @param secret 公众账号密钥
     */
    @Value("${gongbao.wx.secret}")
    public void setSecret(String secret) {
        WeChat.secret = secret; // 保持历史静态字段读取方式。
    }
}
