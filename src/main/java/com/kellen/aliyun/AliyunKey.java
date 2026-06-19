package com.kellen.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 * @author 孙凯伦
 * 
 */
@Component
public class AliyunKey {
    /**
     * AccessKeyId用于标识用户。
     */
    public static String accessKeyId;
    /**
     * AccessKeySecret是用来验证用户的密钥。AccessKeySecret必须保密。
     */
    public static String accessKeySecret;

    @Value("${aliyun.access-key-id:${aliyun.accessKey:}}")
    public void setAccessKeyId(String accessKeyId) {
        AliyunKey.accessKeyId = accessKeyId;
    }

    @Value("${aliyun.access-key-secret:${aliyun.secretKey:}}")
    public void setAccessKeySecret(String accessKeySecret) {
        AliyunKey.accessKeySecret = accessKeySecret;
    }

}
