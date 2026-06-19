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
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.json.JsonUtil;
import com.kellen.aliyun.AliyunKey;
import com.kellen.aliyun.Sms;
import com.kellen.utils.enumeration.SmsEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 阿里云短信发送工具类。
 *
 * @author 孙凯伦
 */
@Slf4j
public final class SmsUtils {

    private SmsUtils() {
    }


    /**
     * 域地址
     */
    final static String DOMAIN = "dysmsapi.aliyuncs.com";
    /**
     * 版本号
     */
    final static String VERSION = "2017-05-25";

    /**
     * 使用默认签名发送模板短信。
     *
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板ID。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @return true 表示阿里云返回 OK，false 表示发送失败或客户端异常
     */
    public static Boolean sendMessage(String phone, SmsEnum smsEnum, String templateParam) {
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
     * 使用指定签名和枚举模板发送短信。
     *
     * @param signName      短信签名
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板ID。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @return true 表示阿里云返回 OK，false 表示发送失败或客户端异常
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
     * 使用指定签名和模板编码发送短信。
     *
     * @param signName      短信签名
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板编码。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @return true 表示阿里云返回 OK，false 表示发送失败或客户端异常
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
     * 使用指定签名和模板编码发送短信，并允许非生产环境按调用方标记决定是否改写接收手机号。
     *
     * @param signName      短信签名
     * @param phone         接收短信的手机号码。支持对多个手机号码发送短信，手机号码之间以英文逗号（,）分隔。上限为1000个手机号码。批量调用相对于单条调用及时性稍有延迟。
     * @param smsEnum       短信模板编码。请在控制台模板管理页面模板CODE一列查看。
     * @param templateParam 短信模板变量对应的实际值，JSON格式。
     * @param isSign        非生产环境是否使用固定测试手机号
     * @return true 表示阿里云返回 OK，false 表示发送失败或客户端异常
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
