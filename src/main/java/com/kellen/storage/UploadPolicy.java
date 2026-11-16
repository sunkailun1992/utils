package com.kellen.storage;

import java.util.Map;

/**
 * 浏览器表单直传对象存储的 POST 策略，由 {@link PrivateObjectStorageService} 生成。
 *
 * <p>客户端把 {@code formFields} 原样提交到 {@code host}；其余字段供展示与排查。</p>
 *
 * @param accessId       平台公开的访问标识（OSS AccessKeyId、COS SecretId、MinIO 的 x-amz-credential），不含密钥。
 * @param policy         Base64 编码后的 POST policy。
 * @param signature      POST 表单签名。
 * @param host           上传地址。
 * @param expire         策略过期时间戳，单位秒。
 * @param objectKey      后端生成的对象键，客户端必须按该键上传。
 * @param fileType       标准化后的文件类型：image/video/audio/file。
 * @param mimeType       标准化后的 MIME 类型。
 * @param maxUploadBytes 单文件上传上限，单位字节。
 * @param formFields     平台签名要求的完整 POST 表单，客户端原样提交。
 */
public record UploadPolicy(
        String accessId,
        String policy,
        String signature,
        String host,
        String expire,
        String objectKey,
        String fileType,
        String mimeType,
        long maxUploadBytes,
        Map<String, String> formFields
) {

    /** 表单字段拷贝成只读，缺省为空。 */
    public UploadPolicy {
        formFields = formFields == null ? Map.of() : Map.copyOf(formFields);
    }
}
