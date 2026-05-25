package com.kellen.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @ClassName Sms
 * @Description 阿里云短信配置
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/4/14 12:58 下午
 */
@Component
public class Sms{
    /**
     * 阿里云短信签名名称。请在控制台签名管理页面签名名称一列查看。
     */
    public static String signName;

    @Value("${aliyun.sms.signName}")
    public void setSignName(String signName) {
        Sms.signName = signName;
    }
}
