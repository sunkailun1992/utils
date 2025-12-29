package com.gb.bean;

import com.timevale.esign.sdk.tech.v3.client.ServiceClientManager;
import com.timevale.tech.sdk.bean.HttpConnectionConfig;
import com.timevale.tech.sdk.bean.ProjectConfig;
import com.timevale.tech.sdk.bean.SignatureConfig;
import com.timevale.tech.sdk.constants.AlgorithmType;
import com.timevale.tech.sdk.constants.HttpType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import com.timevale.esign.sdk.tech.bean.result.Result;
import org.springframework.stereotype.Component;

/**
 * @ClassName Esign
 * @Description e签宝
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2022/1/14 1:52 PM
 */
@Configuration
@Component
public class EsignConfiguration implements ApplicationRunner{


    /**
     * 请求地址
     */
    public static String url;

    /**
     * 应用id
     */
    public static String appid;

    /**
     * 应用密钥
     */
    public static String secret;


    public static Result result;


    /**
     * 启动初始化参数
     * @param args
     */
    @Override
    public void run(ApplicationArguments args) {
        EsignConfiguration.result = ServiceClientManager.registClient(getProjectCfg(), getHttpConCfg(), getSignatureCfg());
    }


    /**
     *
     * description 进行项目配置，如果是测试环境，请联系E签宝交付顾问获取
     */
    public static ProjectConfig getProjectCfg() {
        ProjectConfig proCfg = new ProjectConfig();
        // 项目ID（应用ID）
        proCfg.setProjectId(appid);
        // 项目Secret(应用Secret)
        proCfg.setProjectSecret(secret);
        // 开放平台地址
        // 需要添加ip白名单，而且添加之后需要等五分钟之后才能生效，否则会报错：接口调用方尚未配置ip白名单，请联系e签宝管理员配置
        // 正式环境请求地址：http://openapi.tsign.cn:8080/tgmonitor/rest/app!getAPIInfo2
        // 测试环境请求地址：http://smlitsm.tsign.cn:8080/tgmonitor/rest/app!getAPIInfo2
        proCfg.setItsmApiUrl(url);
        return proCfg;
    }

    /**
     *
     * description http配置
     */
    public static HttpConnectionConfig getHttpConCfg() {
        HttpConnectionConfig httpConCfg = new HttpConnectionConfig();
        // 代理服务IP配置
        httpConCfg.setProxyIp(null);
        // 代理服务端口
        httpConCfg.setProxyPort(null);
        // 协议类型，默认Https
        httpConCfg.setHttpType(HttpType.HTTPS);
        // 请求失败重试次数，默认5次
        httpConCfg.setRetry(null);
        //连接超时时间配置，最大不能超过30秒
        httpConCfg.setTimeoutConnect(30);
        // 请求超时时间，最大不能超过30
        httpConCfg.setTimeoutRequest(30);
        // 代理服务器登录用户名
        httpConCfg.setUsername(null);
        // 代理服务器登录密码
        httpConCfg.setPassword(null);
        return httpConCfg;
    }

    /**
     * description 签名配置
     */
    public static SignatureConfig getSignatureCfg() {
        SignatureConfig signCfg = new SignatureConfig();
        signCfg.setAlgorithm(AlgorithmType.HMACSHA256);

        // 若算法类型是RSA，需要设置e签宝公钥和平台私钥
		/*signCfg.setEsignPublicKey(null);
		signCfg.setPrivateKey(null);*/

        return signCfg;
    }


    @Value("${gongbao.esign.url}")
    public void setUrl(String url) {
        EsignConfiguration.url = url;
    }

    @Value("${gongbao.esign.appid}")
    public void setAppid(String appid) {
        EsignConfiguration.appid = appid;
    }

    @Value("${gongbao.esign.secret}")
    public void setSecret(String secret) {
        EsignConfiguration.secret = secret;
    }

}
