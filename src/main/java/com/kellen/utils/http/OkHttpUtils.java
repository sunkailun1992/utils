package com.kellen.utils.http;

import com.kellen.utils.enumeration.HttpType;
import com.kellen.utils.enumeration.HttpWay;
import com.kellen.utils.json.JsonUtil;
import okhttp3.*;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * OkHttp 请求工具类。
 *
 * @author 孙凯伦
 */
public final class OkHttpUtils {

    private OkHttpUtils() {
    }




    /**
     * 复用的 OkHttp 客户端：类加载时初始化一次，线程安全；使用 JDK 默认的证书与主机名校验。
     * 访问自签名的内部服务时，应为该服务单独配置信任库，不能在这里放开校验。
     */
    private static final class ClientHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .connectTimeout(10, SECONDS)
                .readTimeout(60, SECONDS)
                .build();
    }

    /**
     * 获取 OkHttp 客户端。
     *
     * @return 全局共享的 OkHttp 客户端
     */
    public static OkHttpClient getOkHttpClient() {
        return ClientHolder.CLIENT;
    }

    /**
     * 创建 form 请求体。
     *
     * @param params JSON 格式参数
     * @return form 请求体
     */
    public static RequestBody createRequestParamsForm(String params) {
        Map<String, Object> map = JsonUtil.toBean(params, Map.class);
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
     * 创建 JSON 请求体。
     *
     * @param json JSON 字符串
     * @return JSON 请求体
     */
    public static RequestBody createRequestParamsJson(String json) {
        return RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
    }

    /**
     * 创建 XML 请求体。
     *
     * @param params XML 字符串
     * @return XML 请求体
     */
    public static RequestBody createRequestParamsXml(String params) {
        return RequestBody.create(params, MediaType.parse("application/xml"));
    }

    /**
     * 创建请求对象。
     *
     * @param url     请求地址
     * @param method  请求方法
     * @param body    请求体
     * @param builder 请求构造器
     * @return 请求对象
     */
    public static Request request(String url, String method, RequestBody body, Request.Builder builder) {
        Assert.notNull(url, "url为空");
        return builder.url(url).method(method, body).build();
    }


    /**
     * 发送请求并返回响应体文本：响应在 try-with-resources 里用完即关，非 2xx 状态码抛出 {@link IOException}。
     *
     * @param builder  请求头构造器
     * @param httpWay  请求方式
     * @param url      请求地址
     * @param params   请求参数：表单为 JSON 对象字符串，其余为 JSON 或 XML 原文；GET 时忽略
     * @param httpType 请求体类型
     * @return 响应体文本
     * @throws IOException 网络错误或非 2xx 状态码
     */
    public static String send(Request.Builder builder, HttpWay httpWay, String url, String params, HttpType httpType) throws IOException {
        RequestBody body = null;
        if (httpWay != HttpWay.GET) {
            if (httpType == HttpType.FORM) {
                body = createRequestParamsForm(params);
            } else if (httpType == HttpType.JSON) {
                body = createRequestParamsJson(params);
            } else if (httpType == HttpType.XML) {
                body = createRequestParamsXml(params);
            }
        }
        Request request = request(url, httpWay.getType(), body, builder);
        try (Response response = getOkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP 请求失败，状态码 " + response.code());
            }
            return response.body().string();
        }
    }
}
