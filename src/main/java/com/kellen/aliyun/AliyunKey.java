package com.kellen.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2020/4/9  10:12 上午
 * @email 376253703@qq.com
 * 
 * @explain
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

    @Value("${aliyun.accessKey}")
    public void setAccessKeyId(String accessKeyId) {
        AliyunKey.accessKeyId = accessKeyId;
    }

    @Value("${aliyun.secretKey}")
    public void setAccessKeySecret(String accessKeySecret) {
        AliyunKey.accessKeySecret = accessKeySecret;
    }

}
