package com.gb.utils;


import com.gb.utils.enumeration.HttpType;
import com.gb.utils.enumeration.HttpWay;
import okhttp3.*;
import org.springframework.util.Assert;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * okhttp工具类
 *
 * @author sunkailun
 * @DateTime 2018/5/21  上午11:07
 * @email 376253703@qq.com
 * 
 */
public final class OkhttpUtils {
    /**
     * 安卓手机请求头
     */
    public static final String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 6 Build/LYZ28E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/48.0.2564.23 Mobile Safari/537.36";

    /**
     * mac电脑请求头
     */
    public static final String PC_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.98 Safari/537.36";

    private static OkHttpClient client = null;

    /**
     * 获得okhttp类
     *
     * @param :
     * @return okhttp3.OkHttpClient
     * @author sunkailun
     * @DateTime 2018/5/21  上午11:10
     * @email 376253703@qq.com
     * 
     */
    public static OkHttpClient getOkHttpClient() {
        if (null == client) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(
                            X509Certificate[] chain,
                            String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(
                            X509Certificate[] chain,
                            String authType) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                client = new OkHttpClient.Builder().connectTimeout(10, SECONDS).readTimeout(60, SECONDS)
                        .sslSocketFactory(sslSocketFactory, new X509TrustManager() {
                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType)
                                    throws CertificateException {
                            }

                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType)
                                    throws CertificateException {
                            }
                        })
                        .hostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        })
                        .build();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return client;
    }

    /**
     * 创建请求参数
     *
     * @param params:
     * @return okhttp3.RequestBody
     * @author sunkailun
     * @DateTime 2018/5/21  上午11:09
     * @email 376253703@qq.com
     * 
     */
    public static RequestBody createRequestParamsFrom(String params) {
        Map<String, Object> map = JsonUtil.bean(params, Map.class);
        FormBody.Builder builder = new FormBody.Builder();
        if (null != map && !map.isEmpty()) {
            for (Entry<String, Object> entry : map.entrySet()) {
                if (null != entry.getValue()) {
                    builder.add(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        return builder.build();
    }


    /**
     * 创建请求参数
     *
     * @param json:
     * @return okhttp3.RequestBody
     * @author sunkailun
     * @DateTime 2018/5/21  上午11:09
     * @email 376253703@qq.com
     * 
     */
    public static RequestBody createRequestParamsJson(String json) {
        return RequestBody.create(MediaType.parse("application/json; charset=utf-8"),json);
    }


    /**
     * 创建请求参数
     *
     * @param params:
     * @return okhttp3.RequestBody
     * @author sunkailun
     * @DateTime 2018/5/21  上午11:09
     * @email 376253703@qq.com
     * 
     */
    public static RequestBody createRequestParamsXml(String params) {
        return RequestBody.create(MediaType.parse("application/xml"),params);
    }


    /**
     * 发送请求
     *
     * @param url:
     * @param method:
     * @param body:
     * @return okhttp3.Request
     * @author sunkailun
     * @DateTime 2018/5/21  上午11:18
     * @email 376253703@qq.com
     * 
     */
    public static Request request(String url, String method, RequestBody body, Request.Builder builder) throws UnsupportedEncodingException {
        Assert.notNull(url, "url为空");
        return builder.url(url).method(method, body).build();
    }


    /**
     * 响应内容
     *
     * @param request:
     * @return okhttp3.Response
     * @author sunkailun
     * @DateTime 2018/5/21  上午11:20
     * @email 376253703@qq.com
     * 
     */
    public static Response execute(Request request) throws IOException {
        return getOkHttpClient().newCall(request).execute();
    }


    /**
     * 发送请求
     *
     * @param builder  请求头
     * @param httpWay  请求方式
     * @param url      请求地址
     * @param params   请求参数json或xml
     * @param httpType 类型
     * @return
     * @throws Exception
     */
    public static ResponseBody send(Request.Builder builder, HttpWay httpWay, String url, String params, HttpType httpType) throws Exception {
        RequestBody body = null;
        if(!"GET".equals(httpWay.getType())) {
            if (HttpType.FROM.getType().equals(httpType.getType())) {
                body = createRequestParamsFrom(params);
            } else if (HttpType.JSON.getType().equals(httpType.getType())) {
                body = createRequestParamsJson(params);
            } else if (HttpType.XML.getType().equals(httpType.getType())) {
                body = createRequestParamsXml(params);
            }
        }
        Request request = request(url, httpWay.getType(), body, builder);
        Response response = execute(request);
        return response.body();
    }


}
