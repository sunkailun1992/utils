package com.kellen.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 钉钉
 * 
 */
@Component
public class DingDing {
    /**
     * 钉钉密钥
     */
    public static String secret;
    /**
     * 钉钉机器人地址
     */
    public static String url;

    @Value("${aliyun.dingding.secret}")
    public void setSecret(String secret) {
        DingDing.secret = secret;
    }


    @Value("${aliyun.dingding.url}")
    public void setUrl(String url) {
        DingDing.url = url;
    }
}
