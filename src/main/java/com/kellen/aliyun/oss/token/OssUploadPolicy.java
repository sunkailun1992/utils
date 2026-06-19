package com.kellen.aliyun.oss.token;

/**
 * OSS PostObject 直传策略。
 *
 * @param accessId       OSS AccessKeyId，仅用于本次表单上传，不包含 Secret。
 * @param policy         Base64 编码后的 PostObject policy。
 * @param signature      PostObject 表单签名。
 * @param host           OSS 上传 host。
 * @param expire         策略过期时间戳，单位秒。
 * @param objectKey      后端生成的 objectKey，前端必须按该 key 上传。
 * @param fileType       标准化后的文件类型：image/video/audio/file。
 * @param mimeType       标准化后的 MIME 类型。
 * @param maxUploadBytes 单文件上传上限。
 * @param securityToken  STS 临时令牌，AccessKey 签名模式为空。
 */
public record OssUploadPolicy(
        String accessId,
        String policy,
        String signature,
        String host,
        String expire,
        String objectKey,
        String fileType,
        String mimeType,
        long maxUploadBytes,
        String securityToken
) {
}
