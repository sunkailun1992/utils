package com.kellen.aliyun.sms;

import cn.hutool.extra.spring.SpringUtil;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.kellen.utils.DynamicSourceTtl;
import com.kellen.utils.JsonUtil;
import com.kellen.aliyun.AliyunKey;
import com.kellen.aliyun.Sms;
import com.kellen.utils.enumeration.SmsEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2019/4/9  9:54 AM
 * @email 376253703@qq.com
 * @explain
 */
@Slf4j
public class SmsUtils {

    /**
     * 域地址
     */
    final static String DOMAIN = "dysmsapi.aliyuncs.com";
    /**
     * 版本号
     */
    final static String VERSION = "2017-05-25";

    /**
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板ID。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: sendMessage
     * @description: TODO  阿里云发送短信
     * @return: java.lang.Boolean
     * @date: 2021/4/14 11:47 上午
     */
    public static Boolean sendMessage(String phone, SmsEnum smsEnum, String templateParam) {
        log.debug("手机号{}发送短信{}", phone, templateParam);
        // 判断是否汇中
        if (DynamicSourceTtl.get().equals(DynamicSourceTtl.HZ_DATASOURCE)) {
            return false;
        }
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile("default", AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        //赋值
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("SendSms");
        //手机号，校验环境
        if (SpringUtil.getActiveProfile().equals("prod")) {
            request.putQueryParameter("PhoneNumbers", phone);
        } else {
            request.putQueryParameter("PhoneNumbers", "19941200198");
        }

        //签名
        request.putQueryParameter("SignName", Sms.signName);
        //模板
        request.putQueryParameter("TemplateCode", smsEnum.getValue());
        //模板参数,Json格式
        request.putQueryParameter("TemplateParam", templateParam);
        try {
            CommonResponse response = client.getCommonResponse(request);
            Map<String, Object> map = JsonUtil.bean(response.getData(), Map.class);
            String code = String.valueOf(map.get("Code"));
            if ("OK".equals(code)) {
                return true;
            } else {
                log.error("发送短信失败 {}", map);
                return false;
            }
        } catch (ServerException e) {
            log.error("发送短信失败", e);
        } catch (ClientException e) {
            log.error("发送短信失败", e);
        }
        return false;
    }


    /**
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板ID。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: sendMessage
     * @description: TODO  阿里云发送短信
     * @return: java.lang.Boolean
     * @date: 2021/4/14 11:47 上午
     */
    public static Boolean sendMessage(String signName, String phone, SmsEnum smsEnum, String templateParam) {
        log.debug("手机号{}发送短信{}", phone, templateParam);
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile("default", AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        //赋值
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("SendSms");
        //手机号，校验环境
        if (SpringUtil.getActiveProfile().equals("prod")) {
            request.putQueryParameter("PhoneNumbers", phone);
        } else {
            request.putQueryParameter("PhoneNumbers", "19941200198");
        }

        //签名
        request.putQueryParameter("SignName", signName);
        //模板
        request.putQueryParameter("TemplateCode", smsEnum.getValue());
        //模板参数,Json格式
        request.putQueryParameter("TemplateParam", templateParam);
        try {
            CommonResponse response = client.getCommonResponse(request);
            Map<String, Object> map = JsonUtil.bean(response.getData(), Map.class);
            String code = String.valueOf(map.get("Code"));
            if ("OK".equals(code)) {
                return true;
            } else {
                log.error("发送短信失败 {}", map);
                return false;
            }
        } catch (ServerException e) {
            log.error("发送短信失败", e);
        } catch (ClientException e) {
            log.error("发送短信失败", e);
        }
        return false;
    }



    /**
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板ID。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: sendMessage
     * @description: TODO  阿里云发送短信
     * @return: java.lang.Boolean
     * @date: 2021/4/14 11:47 上午
     */
    public static Boolean sendMessage(String signName, String phone, String smsEnum, String templateParam) {
        log.debug("手机号{}发送短信{}", phone, templateParam);
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile("default", AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        //赋值
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("SendSms");
        //手机号，校验环境
        if (SpringUtil.getActiveProfile().equals("prod")) {
            request.putQueryParameter("PhoneNumbers", phone);
        } else {
            request.putQueryParameter("PhoneNumbers", "19941200198");
        }

        //签名
        request.putQueryParameter("SignName", signName);
        //模板
        request.putQueryParameter("TemplateCode", smsEnum);
        //模板参数,Json格式
        request.putQueryParameter("TemplateParam", templateParam);
        try {
            CommonResponse response = client.getCommonResponse(request);
            Map<String, Object> map = JsonUtil.bean(response.getData(), Map.class);
            String code = String.valueOf(map.get("Code"));
            if ("OK".equals(code)) {
                return true;
            } else {
                log.error("发送短信失败 {}", map);
                return false;
            }
        } catch (ServerException e) {
            log.error("发送短信失败", e);
        } catch (ClientException e) {
            log.error("发送短信失败", e);
        }
        return false;
    }




    /**
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板ID。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: sendMessage
     * @description: TODO  阿里云发送短信
     * @return: java.lang.Boolean
     * @date: 2021/4/14 11:47 上午
     */
    public static Boolean sendMessage(String signName, String phone, String smsEnum, String templateParam,Boolean isSign) {
        log.debug("手机号{}发送短信{}", phone, templateParam);
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile("default", AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        //赋值
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction("SendSms");
        //手机号，校验环境
        if (SpringUtil.getActiveProfile().equals("prod")) {
            request.putQueryParameter("PhoneNumbers", phone);
        } else {
            if(isSign){
                request.putQueryParameter("PhoneNumbers", "19941200198");
            }else{
                request.putQueryParameter("PhoneNumbers", phone);
            }
        }

        //签名
        request.putQueryParameter("SignName", signName);
        //模板
        request.putQueryParameter("TemplateCode", smsEnum);
        //模板参数,Json格式
        request.putQueryParameter("TemplateParam", templateParam);
        try {
            CommonResponse response = client.getCommonResponse(request);
            Map<String, Object> map = JsonUtil.bean(response.getData(), Map.class);
            String code = String.valueOf(map.get("Code"));
            if ("OK".equals(code)) {
                return true;
            } else {
                log.error("发送短信失败 {}", map);
                return false;
            }
        } catch (ServerException e) {
            log.error("发送短信失败", e);
        } catch (ClientException e) {
            log.error("发送短信失败", e);
        }
        return false;
    }
}
