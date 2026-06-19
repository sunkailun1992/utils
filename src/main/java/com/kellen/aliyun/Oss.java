package com.kellen.aliyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 阿里云oss配置
 * 
 */
@Component
public class Oss {
    /**
     * OSS存储对象名称
     */
    public static String bucket;
    /**
     * 阿里云oss外网地址
     */
    public static String endpoint;
    /**
     * 域名地址
     */
    public static String domain;

    @Value("${aliyun.oss.bucket}")
    public void setBucket(String bucket) {
        Oss.bucket = bucket;
        refreshDerivedDomain();
    }

    @Value("${aliyun.oss.domain:}")
    public void setDomain(String domain) {
        Oss.domain = domain;
        refreshDerivedDomain();
    }

    @Value("${aliyun.oss.endpoint}")
    public void setEndpoint(String endpoint) {
        Oss.endpoint = endpoint;
        refreshDerivedDomain();
    }

    /**
     * 返回浏览器或表单直传使用的 OSS host。
     *
     * <p>历史配置使用 {@code aliyun.oss.domain} 显式提供域名；当前 Nacos 公共配置
     * 只提供 bucket 和 endpoint，因此这里保留旧字段并在缺失时自动推导，避免消费者
     * 因为公共配置键名调整而启动失败。</p>
     *
     * @return OSS 访问域名，格式为 {@code https://bucket.endpoint}。
     */
    public static String host() {
        if (StringUtils.hasText(domain)) {
            return domain.trim();
        }
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(endpoint)) {
            return "";
        }
        return buildBucketHost(bucket, endpoint);
    }

    private static void refreshDerivedDomain() {
        if (!StringUtils.hasText(domain) && StringUtils.hasText(bucket) && StringUtils.hasText(endpoint)) {
            domain = buildBucketHost(bucket, endpoint);
        }
    }

    private static String buildBucketHost(String bucket, String endpoint) {
        String safeBucket = bucket.trim();
        String safeEndpoint = endpoint.trim();
        if (safeEndpoint.startsWith("http://")) {
            return "http://" + safeBucket + "." + safeEndpoint.substring("http://".length());
        }
        if (safeEndpoint.startsWith("https://")) {
            return "https://" + safeBucket + "." + safeEndpoint.substring("https://".length());
        }
        return "https://" + safeBucket + "." + safeEndpoint;
    }
}
