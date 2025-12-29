package com.gb.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2020/7/16  2:51 下午
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Component
public class LiveStreaming {
    /**
     * app名称
     */
    public static String appName;
    /**
     * 播放地址
     */
    public static String pullDomain;
    /**
     * 播放秘钥
     */
    public static String pullKey;
    /**
     * 推流地址
     */
    public static String pushDomain;
    /**
     * 推流秘钥
     */
    public static String pushKey;

    @Value("${aliyun.liveStreaming.appName}")
    public void setAppName(String appName) {
        LiveStreaming.appName = appName;
    }

    @Value("${aliyun.liveStreaming.pullDomain}")
    public void setNavicat(String pullDomain) {
        LiveStreaming.pullDomain = pullDomain;
    }

    @Value("${aliyun.liveStreaming.pullKey}")
    public void setPullKey(String pullKey) {
        LiveStreaming.pullKey = pullKey;
    }

    @Value("${aliyun.liveStreaming.pushDomain}")
    public void setPushDomain(String pushDomain) {
        LiveStreaming.pushDomain = pushDomain;
    }

    @Value("${aliyun.liveStreaming.pushKey}")
    public void setPushKey(String pushKey) {
        LiveStreaming.pushKey = pushKey;
    }


}
