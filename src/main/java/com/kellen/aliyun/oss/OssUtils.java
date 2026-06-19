package com.kellen.aliyun.oss;

import com.kellen.aliyun.oss.token.OssTokenProperties;
import com.kellen.aliyun.oss.token.OssTokenUtils;
import com.kellen.aliyun.oss.token.OssUploadPolicy;

import java.net.URI;

/**
 * OSS token 模式公共门面。
 *
 * <p>公共包只保留“后端签发上传策略、业务保存 objectKey 和 token 哈希、展示时后端换短时签名 URL”
 * 这一条路径。旧的服务端直传、公共读 URL、STS 和 OSS 回调验签接口不再保留。</p>
 */
public final class OssUtils {

    private OssUtils() {
    }

    /**
     * 基于公共全局 OSS 配置创建 token 模式参数。
     */
    public static OssTokenProperties tokenProperties(String baseDir,
                                                     long uploadExpireSeconds,
                                                     long viewExpireSeconds,
                                                     long maxUploadBytes) {
        return OssTokenProperties.fromGlobalConfig(baseDir, uploadExpireSeconds, viewExpireSeconds, maxUploadBytes);
    }

    /**
     * 生成 ownerScope 隔离的 PostObject 直传策略。
     */
    public static OssUploadPolicy createUploadPolicy(String ownerScope,
                                                     String fileType,
                                                     String mimeType,
                                                     String fileName,
                                                     OssTokenProperties properties) {
        return OssTokenUtils.createUploadPolicy(ownerScope, fileType, mimeType, fileName, properties);
    }

    /**
     * 校验 objectKey 是否属于当前业务所有者范围。
     */
    public static void validateObjectOwner(OssTokenProperties properties, String ownerScope, String objectKey) {
        OssTokenUtils.validateObjectKeyOwner(properties, ownerScope, objectKey);
    }

    /**
     * 校验 OSS 对象是否已经上传成功。
     */
    public static void verifyObjectExists(OssTokenProperties properties, String objectKey) {
        OssTokenUtils.verifyObjectExists(properties, objectKey);
    }

    /**
     * 为通过业务 token 校验的 objectKey 生成短时签名访问 URL。
     */
    public static URI signedViewUri(OssTokenProperties properties, String objectKey) {
        return OssTokenUtils.signedViewUri(objectKey, properties);
    }

    /**
     * 生成展示 token 明文。调用方只应把哈希持久化到数据库。
     */
    public static String generateDisplayToken() {
        return OssTokenUtils.generateDisplayToken();
    }

    /**
     * 计算展示 token 哈希。
     */
    public static String displayTokenHash(String token) {
        return OssTokenUtils.displayTokenHash(token);
    }

    /**
     * 判断展示 token 明文是否匹配已保存的 token 哈希。
     */
    public static boolean matchesDisplayToken(String token, String expectedHash) {
        return OssTokenUtils.matchesDisplayToken(token, expectedHash);
    }

    /**
     * 获取安全原始文件名。
     */
    public static String safeOriginalName(String originalName, String fallbackObjectKey) {
        return OssTokenUtils.safeOriginalName(originalName, fallbackObjectKey);
    }

    /**
     * 标准化 MIME 类型；未传 MIME 时尝试从文件扩展名推断。
     */
    public static String normalizeMimeType(String mimeType, String fileName) {
        return OssTokenUtils.normalizeMimeType(mimeType, fileName);
    }

    /**
     * 标准化业务文件类型。
     */
    public static String normalizeFileType(String fileType, String mimeType) {
        return OssTokenUtils.normalizeFileType(fileType, mimeType);
    }

    /**
     * 校验 fileType 与 MIME 类型是否匹配。
     */
    public static void validateFileTypeMatchesMime(String fileType, String mimeType) {
        OssTokenUtils.validateFileTypeMatchesMime(fileType, mimeType);
    }

    /**
     * 返回 ownerScope 对应的 OSS 目录前缀。
     */
    public static String ownerBasePrefix(OssTokenProperties properties, String ownerScope) {
        return OssTokenUtils.ownerBasePrefix(properties, ownerScope);
    }
}
