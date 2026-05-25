package com.kellen.utils.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 * @author: 孙凯伦
 * 
 * @email: 376253703@qq.com
 * @description: 邮箱发送账户实体类
 * @date: 2022/1/18 10:24 AM
 *
 */
@Component
public class EmailAccount {
    /**
     * 邮箱服务主机
     */
    public static String host;
    /**
     * SMTP服务端口
     */
    public static Integer port;

    /**
     * 用户名
     */
    public static String user;
    /**
     * 密码,这里可能是授权码
     */
    public static String pass;
    /**
     * 发送方，遵循RFC-822标准
     */
    public static String from;
    /**
     * 使用 SSL安全连接
     */
    public static Boolean sslEnable;

    @Value("${email.host}")
    public  void setHost(String host) {
        EmailAccount.host = host;
    }

    @Value("${email.port}")
    public  void setPort(Integer port) {
        EmailAccount.port = port;
    }

    @Value("${email.user}")
    public  void setUser(String user) {
        EmailAccount.user = user;
    }

    @Value("${email.pass}")
    public  void setPass(String pass) {
        EmailAccount.pass = pass;
    }

    @Value("${email.from}")
    public  void setFrom(String from) {
        EmailAccount.from = from;
    }

    @Value("${email.sslEnable}")
    public  void setSslEnable(Boolean sslEnable) {
        EmailAccount.sslEnable = sslEnable;
    }
}
