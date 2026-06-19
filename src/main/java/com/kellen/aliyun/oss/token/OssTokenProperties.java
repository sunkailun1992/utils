package com.kellen.aliyun.oss.token;

import com.kellen.aliyun.AliyunKey;
import com.kellen.aliyun.Oss;

/**
 * OSS token 模式运行参数。
 *
 * <p>该配置面向“私有 OSS + 后端签名直传 + 后端 token 展示”的通用文件流转模式。
 * 业务服务可以从 Nacos、数据库或本地配置构造本对象；历史项目也可以通过
 * {@link #fromGlobalConfig(String, long, long, long)} 复用 {@code AliyunKey/Oss}
 * 静态配置。</p>
 *
 * @param endpoint            OSS endpoint。
 * @param bucket              OSS bucket。
 * @param accessKeyId         OSS AccessKeyId。
 * @param accessKeySecret     OSS AccessKeySecret。
 * @param baseDir             业务文件根目录。
 * @param uploadExpireSeconds PostObject 策略有效期，单位秒。
 * @param viewExpireSeconds   展示短签名 URL 有效期，单位秒。
 * @param maxUploadBytes      单文件上传上限，单位字节。
 */
public record OssTokenProperties(
        String endpoint,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String baseDir,
        long uploadExpireSeconds,
        long viewExpireSeconds,
        long maxUploadBytes
) {

    /**
     * 基于 utils 历史全局 OSS 配置创建 token 模式配置。
     */
    public static OssTokenProperties fromGlobalConfig(String baseDir,
                                                      long uploadExpireSeconds,
                                                      long viewExpireSeconds,
                                                      long maxUploadBytes) {
        return new OssTokenProperties(Oss.endpoint, Oss.bucket, AliyunKey.accessKeyId, AliyunKey.accessKeySecret,
                baseDir, uploadExpireSeconds, viewExpireSeconds, maxUploadBytes);
    }
}
