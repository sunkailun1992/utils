package com.kellen.aliyun.dingding;

import com.kellen.aliyun.DingDing;
import com.kellen.aliyun.dingding.markdown.MarkDownModel;
import com.kellen.aliyun.dingding.markdown.MarkDownRebootModel;
import com.kellen.aliyun.dingding.text.ContentModel;
import com.kellen.aliyun.dingding.text.TextRebootModel;
import com.kellen.utils.json.JsonUtil;
import com.kellen.utils.http.OkHttpUtils;
import com.kellen.utils.enumeration.HttpType;
import com.kellen.utils.enumeration.HttpWay;
import okhttp3.Request;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName DingDingUtil
 * @Description
 * @Author 孙凯伦
 * @Email 376253703@qq.com
 * @Time 2021/6/25 9:14 上午
 */
public class DingDingUtil {

    /**
     * 组装 发送的信息
     * Text版本
     *
     * @param isAt       是否需要 @所有人
     * @param msgContent 要发送信息的主体
     * @param telList    要 @人的电话号码,如果@单独的几个人，就传一个空list，而不是 null
     * @return
     */
    public static String test(boolean isAt, String msgContent, List<String> telList) {
        //提醒设置
        AtMobiles atMobiles = new AtMobiles();
        atMobiles.setIsAtAll(isAt);
        atMobiles.setAtMobiles(telList);
        //消息设置
        ContentModel contentModel = new ContentModel();
        contentModel.setContent(msgContent);
        //发送设置
        TextRebootModel model = new TextRebootModel();
        model.setAt(atMobiles);
        model.setText(contentModel);
        //转换json
        String json = JsonUtil.json(model);
        return json;
    }

    /**
     * 组装 发送的信息
     * Markdown格式
     *
     * @param isAt       是否需要 @所有人
     * @param title      标题
     * @param msgContent 要发送信息的主体
     * @param telList    要 @人的电话号码,如果@单独的几个人，就传一个空list，而不是 null
     * @return
     */
    public static String markdown(boolean isAt, String title, String msgContent, List<String> telList) {
        //提醒设置
        AtMobiles atMobiles = new AtMobiles();
        atMobiles.setIsAtAll(isAt);
        atMobiles.setAtMobiles(telList);
        //消息设置
        MarkDownModel markDownModel = new MarkDownModel();
        markDownModel.setTitle(title);
        markDownModel.setText(msgContent);
        //发送设置
        MarkDownRebootModel model = new MarkDownRebootModel();
        model.setAt(atMobiles);
        model.setMarkdown(markDownModel);
        //转换json
        String json = JsonUtil.json(model);
        return json;
    }

    /**
     * post 请求，发送给哪一个机器人
     *
     * @param url     机器人的token
     * @param message 发送的消息
     * @return
     */
    public static String sendPost(String url, String message) throws Exception {
        String json = OkHttpUtils.send(new Request.Builder(), HttpWay.POST, url, message, HttpType.JSON).string();
        return json;
    }

    /**
     * 选择加签方式下的加签方法
     *
     * @param secret 密钥，机器人安全设置页面，加签一栏下面显示的SEC开头的字符串
     * @return
     */
    public static Map<String, String> dingDingSec(String secret) throws Exception {
        Long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");
        Map<String, String> map = new HashMap<>();
        map.put("sign", sign);
        map.put("timestamp", timestamp.toString());
        return map;
    }

    /**
     * 加签机器人实现，这里需要注意的是：timestamp和sign需要保持一致
     *
     * @param message 要发送的信息
     * @return
     * @throws Exception
     */
    public static String sendReboot(String message) throws Exception {
        Map<String, String> map = dingDingSec(DingDing.secret);
        String sign = map.get("sign");
        String timestamp = map.get("timestamp");
        StringBuffer stringBuffer = new StringBuffer();
        String robotUrl = stringBuffer.append(DingDing.url).append("&timestamp=").append(timestamp).append("&sign=").append(sign).toString();
        return sendPost(robotUrl, message);
    }

    /**
     * 加签机器人实现，这里需要注意的是：timestamp和sign需要保持一致
     *
     * @param message 要发送的信息
     * @return
     * @throws Exception
     */
    public static String sendReboot(String message, String url, String secret) throws Exception {
        Map<String, String> map = dingDingSec(secret);
        String sign = map.get("sign");
        String timestamp = map.get("timestamp");
        StringBuffer stringBuffer = new StringBuffer();
        String robotUrl = stringBuffer.append(url).append("&timestamp=").append(timestamp).append("&sign=").append(sign).toString();
        return sendPost(robotUrl, message);
    }

    /**
     * 关键字机器人：发送消息中需要有对应的关键字才能发送成功
     *
     * @param message 封装的消息
     * @return
     * @throws Exception
     */
    public static String sendKeyReboot(String message) throws Exception {
        return sendPost(DingDing.url, message);
    }

    public static void main(String[] args) {
        //text类型的消息：
        String text = DingDingUtil.test(false, "测试", Arrays.asList("13777579028"));

        //markDown类型的消息：使用于字体加粗，超链接隐藏
        String markDown = DingDingUtil.markdown(false, "杭州天气", "@13777579028 @17767259525  #### 杭州天气 \n > 9度，西北风1级，空气良89，相对温度73%\n > ![screenshot](https://img.alicdn.com/tfs/TB1NwmBEL9TBuNjy1zbXXXpepXa-2400-1218.png)\n > ###### 10点20分发布 [天气](https://www.dingtalk.com) \n", Arrays.asList("13777579028", "17767259525"));

        //钉钉机器人推送
        try {
            String message = DingDingUtil.test(false, "测试", null);
            String i = DingDingUtil.sendReboot(message,"https://oapi.dingtalk.com/robot/send?access_token=72c3ae1bf443763eeef644372d71eb848758a2703b4a4dc120c9a20259acbc8e","SECc6fc6d79efa9a9e560e4d5ed6b5c91172b107c95ad2c23a0bbe2e5427cb1eec0");
            System.out.printf("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
