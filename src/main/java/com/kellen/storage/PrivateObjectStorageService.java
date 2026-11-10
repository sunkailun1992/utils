package com.kellen.storage;

import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import com.kellen.aliyun.oss.token.OssTokenUtils;
import com.kellen.aliyun.oss.token.OssUploadPolicy;
import com.qcloud.cos.auth.COSSigner;
import cn.hutool.json.JSONUtil;
import io.minio.PostPolicy;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.constant.Constant;
import org.dromara.x.file.storage.core.platform.AliyunOssFileStorage;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import org.dromara.x.file.storage.core.platform.TencentCosFileStorage;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 私有对象存储适配器。业务服务保留所有者、展示 token、持久化和授权职责。
 *
 * <p>上传、存在性检查、GET 签名均由 X File Storage 处理。其 2.3.0 没有 POST policy API，
 * 因而仅表单签名按原生协议适配 OSS / MinIO / COS；不另建 SDK 客户端或维护第二套凭据。</p>
 */
@Service
public class PrivateObjectStorageService {
    private final FileStorageService files;

    public PrivateObjectStorageService(FileStorageService files) {
        this.files = files;
    }

    public String platform() {
        return files.getProperties().getDefaultPlatform();
    }

    /** 未配置私有存储时保持服务可启动，实际文件操作失败关闭。 */
    public boolean configured() {
        FileStorage storage = files.getFileStorage(platform());
        return storage instanceof MinioFileStorage || storage instanceof AliyunOssFileStorage
                || storage instanceof TencentCosFileStorage;
    }

    /** 显式选择平台后，即使配置不完整也必须走统一入口并报错，不能静默回退旧云存储。 */
    public boolean selected() {
        return platform() != null && !platform().isBlank() && !"local".equals(platform());
    }

    public OssUploadPolicy createUploadPolicy(String ownerScope, String fileType, String mimeType, String fileName,
                                               String baseDir, long uploadExpireSeconds, long viewExpireSeconds,
                                               long maxUploadBytes) {
        String mime = OssTokenUtils.normalizeMimeType(mimeType, fileName);
        String type = OssTokenUtils.normalizeFileType(fileType, mime);
        OssTokenUtils.validateFileTypeMatchesMime(type, mime);
        if (maxUploadBytes < 1) {
            throw new IllegalArgumentException("maxUploadBytes 必须大于零");
        }
        String key = OssTokenUtils.createObjectKey(baseDir, ownerScope, type, fileName, mime);
        Date expiration = expiration(uploadExpireSeconds);
        FileStorage storage = requiredStorage(platform());
        try {
            if (storage instanceof MinioFileStorage minio) {
                PostPolicy policy = new PostPolicy(minio.getBucketName(),
                        ZonedDateTime.ofInstant(expiration.toInstant(), ZoneOffset.UTC));
                policy.addEqualsCondition("key", key);
                policy.addEqualsCondition("Content-Type", mime);
                policy.addContentLengthRangeCondition(1, maxUploadBytes);
                Map<String, String> fields = new LinkedHashMap<>(minio.getClient().getPresignedPostFormData(policy));
                fields.put("key", key);
                fields.put("Content-Type", mime);
                String host = minioUploadHost(minio.getPlatform(), minio.getBucketName());
                return result(fields.get("x-amz-credential"), fields.get("policy"), fields.get("x-amz-signature"),
                        host, expiration, key, type, mime, maxUploadBytes, fields);
            }
            if (storage instanceof TencentCosFileStorage cos) {
                var config = files.getProperties().getTencentCos().stream()
                        .filter(item -> item.getPlatform().equals(cos.getPlatform())).findFirst().orElseThrow();
                String keyTime = Instant.now().getEpochSecond() + ";" + expiration.toInstant().getEpochSecond();
                var conditions = List.of(Map.of("bucket", config.getBucketName()), Map.of("key", key),
                        Map.of("Content-Type", mime), Map.of("q-sign-algorithm", "sha1"),
                        Map.of("q-ak", config.getSecretId()), Map.of("q-sign-time", keyTime),
                        Map.of("success_action_status", "200"), List.of("content-length-range", 1, maxUploadBytes));
                String policy = JSONUtil.toJsonStr(Map.of("expiration", expiration.toInstant().toString(),
                        "conditions", conditions));
                String encoded = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
                String signature = new COSSigner().buildPostObjectSignature(config.getSecretKey(), keyTime, policy);
                Map<String, String> fields = Map.of("key", key, "policy", encoded, "q-signature", signature,
                        "q-ak", config.getSecretId(), "q-sign-algorithm", "sha1", "q-key-time", keyTime,
                        "Content-Type", mime, "success_action_status", "200");
                String host = endpoint("https://" + config.getBucketName() + ".cos."
                        + config.getRegion() + ".myqcloud.com").toString();
                return result(config.getSecretId(), encoded, signature, host, expiration,
                        key, type, mime, maxUploadBytes, fields);
            }
            AliyunOssFileStorage oss = (AliyunOssFileStorage) storage;
            PolicyConditions conditions = new PolicyConditions();
            conditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 1, maxUploadBytes);
            conditions.addConditionItem(MatchMode.Exact, PolicyConditions.COND_KEY, key);
            conditions.addConditionItem(MatchMode.Exact, "Content-Type", mime);
            String policy = oss.getClient().generatePostPolicy(expiration, conditions);
            String encoded = BinaryUtil.toBase64String(policy.getBytes(StandardCharsets.UTF_8));
            String signature = oss.getClient().calculatePostSignature(policy);
            var config = files.getProperties().getAliyunOss().stream()
                    .filter(item -> item.getPlatform().equals(oss.getPlatform())).findFirst().orElseThrow();
            String endpoint = endpoint(config.getEndPoint()).toString();
            URI uri = URI.create(endpoint);
            String host = uri.getScheme() + "://" + config.getBucketName() + "." + uri.getRawAuthority();
            Map<String, String> fields = Map.of("key", key, "policy", encoded, "signature", signature,
                    "OSSAccessKeyId", config.getAccessKey(), "Content-Type", mime, "success_action_status", "200");
            return result(config.getAccessKey(), encoded, signature, host, expiration, key, type, mime, maxUploadBytes, fields);
        } catch (Exception exception) {
            // SDK 错误可能包含 endpoint / 凭据上下文，调用方只收到稳定消息。
            throw new IllegalStateException("无法生成对象存储上传策略");
        }
    }

    public void validateObjectOwner(String baseDir, String ownerScope, String objectKey) {
        String key = requiredKey(objectKey);
        if (!key.startsWith(OssTokenUtils.ownerBasePrefix(baseDir, ownerScope))) {
            throw new IllegalArgumentException("objectKey 不属于当前用户");
        }
    }

    public void verifyObjectExists(String objectKey) {
        verifyObjectExists(platform(), objectKey);
    }

    public void verifyObjectExists(String platform, String objectKey) {
        if (!files.exists(fileInfo(platform, objectKey))) {
            throw new IllegalArgumentException("文件尚未上传成功");
        }
    }

    public URI signedViewUri(String objectKey, long expireSeconds) {
        return signedViewUri(platform(), objectKey, expireSeconds);
    }

    public URI signedViewUri(String platform, String objectKey, long expireSeconds) {
        FileInfo file = fileInfo(platform, objectKey);
        var result = files.generatePresignedUrl(platform).setPath(file.getPath()).setFilename(file.getFilename())
                .setMethod(Constant.GeneratePresignedUrl.Method.GET).setExpiration(expiration(expireSeconds))
                .generatePresignedUrl();
        if (result == null || result.getUrl() == null) {
            throw new IllegalStateException("无法生成对象存储访问地址");
        }
        return URI.create(result.getUrl());
    }

    /** 服务端生成的文件也通过同一平台上传，不返回可绕过业务 token 的公开地址。 */
    public void putObject(String objectKey, String mimeType, long length, InputStream source) {
        FileInfo info = fileInfo(platform(), objectKey);
        if (length < 0) throw new IllegalArgumentException("contentLength 不能为负数");
        FileInfo saved = files.of(Objects.requireNonNull(source), info.getFilename(), mimeType, length)
                .setPlatform(info.getPlatform()).setPath(info.getPath()).setSaveFilename(info.getFilename()).upload();
        if (saved == null) throw new IllegalStateException("对象存储上传失败");
    }

    private FileInfo fileInfo(String platform, String objectKey) {
        FileStorage storage = requiredStorage(platform);
        String key = requiredKey(objectKey);
        int slash = key.lastIndexOf('/');
        return new FileInfo().setPlatform(platform).setBasePath(basePath(storage))
                .setPath(slash < 0 ? "" : key.substring(0, slash + 1)).setFilename(key.substring(slash + 1));
    }

    private FileStorage requiredStorage(String platform) {
        FileStorage storage = files.getFileStorage(platform);
        if (!(storage instanceof MinioFileStorage) && !(storage instanceof AliyunOssFileStorage)
                && !(storage instanceof TencentCosFileStorage)) {
            throw new IllegalStateException("未配置可用的私有对象存储平台");
        }
        // key 已含业务根目录，禁止平台基础路径二次拼接后使 POST 与 GET 指向不同对象。
        if (basePath(storage) != null && !basePath(storage).isEmpty()) {
            throw new IllegalStateException("私有对象存储平台 base-path 必须为空");
        }
        return storage;
    }

    private static String basePath(FileStorage storage) {
        if (storage instanceof MinioFileStorage minio) return minio.getBasePath();
        if (storage instanceof AliyunOssFileStorage oss) return oss.getBasePath();
        return ((TencentCosFileStorage) storage).getBasePath();
    }

    private String minioUploadHost(String platform, String bucket) {
        var config = files.getProperties().getMinio().stream()
                .filter(item -> item.getPlatform().equals(platform)).findFirst().orElseThrow();
        return endpoint(config.getEndPoint()).toString().replaceAll("/+$", "") + "/" + bucket;
    }

    private static URI endpoint(String value) {
        URI uri = URI.create(value.contains("://") ? value : "https://" + value);
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("对象存储 endpoint 格式不合法");
        }
        return uri;
    }

    private static String requiredKey(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("..")
                || value.contains("\\") || value.contains("//") || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("objectKey 不合法");
        }
        return value;
    }

    private static Date expiration(long seconds) {
        if (seconds < 1 || seconds > 604800) throw new IllegalArgumentException("签名有效期必须在 1–604800 秒之间");
        return Date.from(Instant.now().plusSeconds(seconds));
    }

    private static OssUploadPolicy result(String accessId, String policy, String signature, String host, Date expiration,
                                           String key, String type, String mime, long maxBytes, Map<String, String> fields) {
        return new OssUploadPolicy(accessId, policy, signature, host, Long.toString(expiration.getTime() / 1000),
                key, type, mime, maxBytes, null, fields);
    }
}
