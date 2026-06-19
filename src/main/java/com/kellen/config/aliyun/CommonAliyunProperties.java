package com.kellen.config.aliyun;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 公共阿里云配置绑定。
 *
 * <p>公共配置中心使用顶层 {@code aliyun.*} 给多个服务共享 OSS、短信、钉钉等配置。
 * 业务服务可按需读取 OSS 等子配置，避免同一 bucket 和 AccessKey 在多个 Data ID 中重复维护。</p>
 *
 * @param accessKeyId     公共阿里云 AccessKeyId。
 * @param accessKeySecret 公共阿里云 AccessKeySecret。
 * @param oss             公共 OSS 配置段。
 */
@ConfigurationProperties(prefix = "aliyun")
public record CommonAliyunProperties(
        @DefaultValue("") String accessKeyId,
        @DefaultValue("") String accessKeySecret,
        @DefaultValue OssProperties oss
) {

    /**
     * 公共 OSS 配置。
     *
     * @param endpoint            OSS endpoint。
     * @param bucket              OSS bucket。
     * @param baseDir             文件根目录。
     * @param uploadExpireSeconds 直传策略有效期。
     * @param viewExpireSeconds   展示签名有效期。
     * @param maxUploadBytes      单文件上传上限。
     */
    public record OssProperties(
            @DefaultValue("") String endpoint,
            @DefaultValue("") String bucket,
            @DefaultValue("ai-miniapp") String baseDir,
            @DefaultValue("60") long uploadExpireSeconds,
            @DefaultValue("1800") long viewExpireSeconds,
            @DefaultValue("52428800") long maxUploadBytes
    ) {
    }
}
