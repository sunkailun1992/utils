package com.kellen.aliyun.oss.token;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 私有 OSS token 模式通用工具。
 *
 * <p>该工具沉淀自 AI 小程序文件服务：客户端只拿短时 PostObject 策略直传 OSS；
 * 业务侧只保存 objectKey 和展示 token 哈希；展示时先由业务服务校验 token，再调用
 * {@link #signedViewUri(String, OssTokenProperties)} 生成短时 OSS 签名 URL。</p>
 */
public final class OssTokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> FILE_TYPES = Set.of("image", "video", "audio", "file");
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/json",
            "text/plain",
            "text/markdown",
            "text/csv"
    );
    private static final Map<String, String> EXTENSION_MIME_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("json", "application/json"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("m4v", "video/x-m4v"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("m4a", "audio/mp4")
    );

    private OssTokenUtils() {
    }

    /**
     * 创建短时 PostObject 直传策略，并生成 ownerScope 隔离的 objectKey。
     *
     * @param ownerScope 业务所有者范围，例如 {@code wechat-user-1001} 或 {@code tenant-1/user-2}。
     */
    public static OssUploadPolicy createUploadPolicy(String ownerScope,
                                                     String fileType,
                                                     String mimeType,
                                                     String fileName,
                                                     OssTokenProperties properties) {
        OssTokenProperties oss = requiredProperties(properties);
        String normalizedOwnerScope = normalizeOwnerScope(ownerScope);
        String normalizedMimeType = normalizeMimeType(mimeType, fileName);
        String normalizedFileType = normalizeFileType(fileType, normalizedMimeType);
        validateFileTypeMatchesMime(normalizedFileType, normalizedMimeType);
        String objectKey = buildObjectKey(oss, normalizedOwnerScope, normalizedFileType, fileName, normalizedMimeType);

        OSS ossClient = buildClient(oss);
        try {
            long expireSeconds = Math.max(30, oss.uploadExpireSeconds());
            long expireEndTime = System.currentTimeMillis() + expireSeconds * 1000;
            Date expiration = new Date(expireEndTime);
            PolicyConditions policyConditions = new PolicyConditions();
            policyConditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, Math.max(1, oss.maxUploadBytes()));
            policyConditions.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, objectKey);

            String postPolicy = ossClient.generatePostPolicy(expiration, policyConditions);
            String encodedPolicy = BinaryUtil.toBase64String(postPolicy.getBytes(StandardCharsets.UTF_8));
            String postSignature = ossClient.calculatePostSignature(postPolicy);
            return new OssUploadPolicy(oss.accessKeyId().trim(), encodedPolicy, postSignature, host(oss),
                    String.valueOf(expireEndTime / 1000), objectKey, normalizedFileType, normalizedMimeType,
                    oss.maxUploadBytes(), null);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 验证 objectKey 是否属于当前业务所有者范围。
     */
    public static void validateObjectKeyOwner(OssTokenProperties properties, String ownerScope, String objectKey) {
        String requiredObjectKey = requiredText(objectKey, "objectKey");
        if (!requiredObjectKey.startsWith(ownerBasePrefix(properties, ownerScope))) {
            throw new IllegalArgumentException("objectKey 不属于当前用户");
        }
    }

    /**
     * 校验 OSS 对象是否已经上传成功。
     */
    public static void verifyObjectExists(OssTokenProperties properties, String objectKey) {
        OssTokenProperties oss = requiredProperties(properties);
        String requiredObjectKey = requiredText(objectKey, "objectKey");
        OSS ossClient = buildClient(oss);
        try {
            if (!ossClient.doesObjectExist(oss.bucket().trim(), requiredObjectKey)) {
                throw new IllegalArgumentException("文件尚未上传成功");
            }
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 为已经通过业务 token 校验的 objectKey 生成短时 GET 签名 URL。
     */
    public static URI signedViewUri(String objectKey, OssTokenProperties properties) {
        OssTokenProperties oss = requiredProperties(properties);
        String requiredObjectKey = requiredText(objectKey, "objectKey");
        OSS ossClient = buildClient(oss);
        try {
            Date expiration = new Date(System.currentTimeMillis() + Math.max(30, oss.viewExpireSeconds()) * 1000);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(oss.bucket().trim(), requiredObjectKey, HttpMethod.GET);
            request.setExpiration(expiration);
            return URI.create(ossClient.generatePresignedUrl(request).toString());
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 生成展示 token 明文。调用方只应把哈希持久化到数据库。
     */
    public static String generateDisplayToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算展示 token 的 SHA-256 哈希。
     */
    public static String displayTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(requiredText(token, "token").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to hash token", exception);
        }
    }

    /**
     * 判断展示 token 明文是否匹配已保存的 token 哈希。
     */
    public static boolean matchesDisplayToken(String token, String expectedHash) {
        return displayTokenHash(token).equals(requiredText(expectedHash, "displayTokenHash"));
    }

    /**
     * 生成 OSS 上传 host。
     */
    public static String host(OssTokenProperties properties) {
        OssTokenProperties oss = requiredProperties(properties);
        String endpoint = oss.endpoint().trim();
        if (endpoint.startsWith("http://")) {
            return "http://" + oss.bucket().trim() + "." + endpoint.substring("http://".length());
        }
        if (endpoint.startsWith("https://")) {
            return "https://" + oss.bucket().trim() + "." + endpoint.substring("https://".length());
        }
        return "https://" + oss.bucket().trim() + "." + endpoint;
    }

    /**
     * 标准化业务文件类型。
     */
    public static String normalizeFileType(String fileType, String mimeType) {
        if (hasText(fileType)) {
            String normalized = fileType.trim().toLowerCase(Locale.ROOT);
            if (FILE_TYPES.contains(normalized)) {
                return normalized;
            }
        }
        String requiredMimeType = requiredText(mimeType, "mimeType");
        if (requiredMimeType.startsWith("image/")) {
            return "image";
        }
        if (requiredMimeType.startsWith("video/")) {
            return "video";
        }
        if (requiredMimeType.startsWith("audio/")) {
            return "audio";
        }
        return "file";
    }

    /**
     * 标准化 MIME 类型；未传 MIME 时尝试从文件扩展名推断。
     */
    public static String normalizeMimeType(String mimeType, String fileName) {
        if (hasText(mimeType)) {
            return mimeType.trim().toLowerCase(Locale.ROOT);
        }
        String extension = rawExtension(fileName);
        String inferred = EXTENSION_MIME_TYPES.get(extension);
        if (hasText(inferred)) {
            return inferred;
        }
        throw new IllegalArgumentException("mimeType 不能为空，且无法从文件名推断");
    }

    /**
     * 校验 fileType 与 MIME 类型是否匹配。
     */
    public static void validateFileTypeMatchesMime(String fileType, String mimeType) {
        String requiredFileType = requiredText(fileType, "fileType");
        String requiredMimeType = requiredText(mimeType, "mimeType");
        boolean matched = switch (requiredFileType) {
            case "image" -> requiredMimeType.startsWith("image/");
            case "video" -> requiredMimeType.startsWith("video/");
            case "audio" -> requiredMimeType.startsWith("audio/");
            case "file" -> DOCUMENT_MIME_TYPES.contains(requiredMimeType) || requiredMimeType.startsWith("text/");
            default -> false;
        };
        if (!matched) {
            throw new IllegalArgumentException("fileType 与 mimeType 不匹配");
        }
    }

    /**
     * 获取安全原始文件名，避免路径穿透和超长文件名污染业务表。
     */
    public static String safeOriginalName(String originalName, String fallbackObjectKey) {
        String source = hasText(originalName) ? originalName : fallbackObjectKey;
        String normalized = source == null ? "" : source.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!hasText(filename)) {
            return "upload";
        }
        String trimmed = filename.trim();
        if (trimmed.length() > 255) {
            return trimmed.substring(trimmed.length() - 255);
        }
        return trimmed;
    }

    /**
     * 返回 ownerScope 对应的 OSS 目录前缀。
     */
    public static String ownerBasePrefix(OssTokenProperties properties, String ownerScope) {
        OssTokenProperties oss = requiredProperties(properties);
        String baseDir = requiredText(oss.baseDir(), "oss.baseDir")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        return baseDir + "/" + normalizeOwnerScope(ownerScope) + "/";
    }

    private static String buildObjectKey(OssTokenProperties oss, String ownerScope, String fileType, String fileName, String mimeType) {
        String extension = extension(fileName, mimeType);
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        return ownerBasePrefix(oss, ownerScope) + fileType + "/" + datePath + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private static String extension(String fileName, String mimeType) {
        String rawExtension = rawExtension(fileName);
        if (hasText(rawExtension)) {
            return rawExtension;
        }
        if ("image/jpeg".equals(mimeType)) {
            return "jpg";
        }
        if (mimeType != null && mimeType.contains("/")) {
            String subtype = mimeType.substring(mimeType.indexOf('/') + 1).replace("+", "-");
            String normalized = subtype.replaceAll("[^a-z0-9-]", "");
            if (hasText(normalized)) {
                return normalized;
            }
        }
        return "bin";
    }

    private static String rawExtension(String fileName) {
        String filename = safeOriginalName(fileName, "");
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (extension.length() > 16) {
            return "";
        }
        return extension;
    }

    private static OSS buildClient(OssTokenProperties oss) {
        return new OSSClientBuilder().build(oss.endpoint().trim(), oss.accessKeyId().trim(), oss.accessKeySecret().trim());
    }

    private static OssTokenProperties requiredProperties(OssTokenProperties properties) {
        if (properties == null || !hasText(properties.endpoint()) || !hasText(properties.bucket())
                || !hasText(properties.accessKeyId()) || !hasText(properties.accessKeySecret())) {
            throw new IllegalStateException("OSS endpoint、bucket、accessKeyId 或 accessKeySecret 未配置");
        }
        return properties;
    }

    private static String normalizeOwnerScope(String ownerScope) {
        String normalized = requiredText(ownerScope, "ownerScope")
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (!hasText(normalized) || normalized.contains("..") || normalized.contains("//")) {
            throw new IllegalArgumentException("ownerScope 不合法");
        }
        return normalized;
    }

    private static String requiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
