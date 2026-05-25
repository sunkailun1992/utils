package com.kellen.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @ClassName Oss
 * @Description 阿里云oss配置
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/4/14 12:56 下午
 */
@Component
public class Oss {
    /**
     * OSS存储对象名称
     */
    public static String bucket;
    /**
     * 阿里云oss外网地址
     */
    public static String endpoint;
    /**
     * 域名地址
     */
    public static String domain;

    @Value("${aliyun.oss.bucket}")
    public void setBucket(String bucket) {
        Oss.bucket = bucket;
    }

    @Value("${aliyun.oss.domain}")
    public void setDomain(String domain) {
        Oss.domain = domain;
    }

    @Value("${aliyun.oss.endpoint}")
    public void setEndpoint(String endpoint) {
        Oss.endpoint = endpoint;
    }
}
