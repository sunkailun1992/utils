package com.kellen.aliyun.live;

import com.kellen.aliyun.LiveStreaming;
import com.google.common.collect.Maps;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;

/**
 * 阿里云直播流工具类。
 *
 * @author 孙凯伦
 */
public final class CreateLiveUtils {

    private CreateLiveUtils() {
    }


    /**
     * 计算md5
     *
     * @param param
     * @return
     */
    public static String md5(String param) {
        if (param == null || param.length() == 0) {
            return null;
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(param.getBytes());
            byte[] byteArray = md5.digest();

            BigInteger bigInt = new BigInteger(1, byteArray);
            // 参数16表示16进制
            String result = bigInt.toString(16);
            // 不足32位高位补零
            while (result.length() < 32) {
                result = "0" + result;
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }


    /**
     * 生成推流地址
     *
     * @param streamName 推流StreamName
     * @param endDate    过期时间
     */
    public static Map<String, Object> generatePushUrl(String streamName, Date endDate) {
        Map<String, Object> map = Maps.newHashMap();
        /**
         * 过期时间
         */
        Long timeStamp = endDate.getTime();
        /**
         * 推流地址
         */
        String stringToMd5 = "/" + LiveStreaming.appName + "/" + streamName + "-" + timeStamp + "-0-0-" + LiveStreaming.pushKey;
        String authKey = md5(stringToMd5);
        map.put("pushUrl", "rtmp://" + LiveStreaming.pushDomain + "/" + LiveStreaming.appName + "/" + streamName + "?auth_key=" + timeStamp + "-0-0-" + authKey);
        return map;
    }


    /**
     * @param streamName 播放streamName (同推流streamName）
     * @param endDate    过期时间
     * @return
     */
    public static Map<String, Object> generalPullUrl(String streamName, Date endDate) {
        Map<String, Object> map = Maps.newHashMap();
        /**
         * 过期时间
         */
        Long timeStamp = endDate.getTime();
        /**
         * rtmp的拉流地址
         */
        String rtmpToMd5 = "/" + LiveStreaming.appName + "/" + streamName + "-" + timeStamp + "-0-0-" + LiveStreaming.pullKey;
        String rtmpAuthKey = md5(rtmpToMd5);
        map.put("rtmpUrl", "rtmp://" + LiveStreaming.pullDomain + "/" + LiveStreaming.appName + "/" + streamName + "?auth_key=" + timeStamp + "-0-0-" + rtmpAuthKey);
        /**
         * m3u8的拉流地址
         */
        String hlsToMd5 = "/" + LiveStreaming.appName + "/" + streamName + ".m3u8-" + timeStamp + "-0-0-" + LiveStreaming.pullKey;
        String hlsAuthKey = md5(hlsToMd5);
        map.put("hlsUrl", "https://" + LiveStreaming.pullDomain + "/" + LiveStreaming.appName + "/" + streamName + ".m3u8" + "?auth_key=" + timeStamp + "-0-0-" + hlsAuthKey);
        /**
         * flv的拉流地址
         */
        String flvToMd5 = "/" + LiveStreaming.appName + "/" + streamName + ".flv-" + timeStamp + "-0-0-" + LiveStreaming.pullKey;
        String flvAuthKey = md5(flvToMd5);
        map.put("flvUrl", "https://" + LiveStreaming.pullDomain + "/" + LiveStreaming.appName + "/" + streamName + ".flv" + "?auth_key=" + timeStamp + "-0-0-" + flvAuthKey);
        return map;
    }
}