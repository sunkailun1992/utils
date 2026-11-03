package com.kellen.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 显式启用后才访问个人测试桶；只允许创建 kellen-github-dev，不修改桶策略或保存凭据。 */
@EnabledIfEnvironmentVariable(named = "XFS_SMOKE_ENABLED", matches = "true")
class MinioStorageSmokeTest {
    @Test
    void postUploadExistsSignedGetAndSizeLimitUseTheSameXFileStoragePlatform() {
        new ApplicationContextRunner().withUserConfiguration(ObjectStorageConfig.class)
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withPropertyValues("dromara.x-file-storage.default-platform=smoke-minio",
                        "dromara.x-file-storage.minio[0].platform=smoke-minio",
                        "dromara.x-file-storage.minio[0].enable-storage=true",
                        "dromara.x-file-storage.minio[0].end-point=" + requiredEnvironment("XFS_SMOKE_ENDPOINT"),
                        "dromara.x-file-storage.minio[0].bucket-name=" + requiredEnvironment("XFS_SMOKE_BUCKET"),
                        "dromara.x-file-storage.minio[0].access-key=" + requiredEnvironment("XFS_SMOKE_ACCESS_KEY"),
                        "dromara.x-file-storage.minio[0].secret-key=" + requiredEnvironment("XFS_SMOKE_SECRET_KEY"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FileStorageService files = context.getBean(FileStorageService.class);
                    String bucket = requiredEnvironment("XFS_SMOKE_BUCKET");
                    if (!"kellen-github-dev".equals(bucket)) {
                        throw new IllegalArgumentException("冒烟测试只允许 kellen-github-dev 桶");
                    }
                    MinioFileStorage minio = files.getFileStorage("smoke-minio");
                    if (!minio.getClient().bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                        minio.getClient().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    }
                    PrivateObjectStorageService storage = new PrivateObjectStorageService(files);
                    String owner = "smoke-" + UUID.randomUUID();
                    var policy = storage.createUploadPolicy(owner, "file", "text/plain", "smoke.txt",
                            "github", 60, 60, 16);
                    try {
                        byte[] payload = "xfs-smoke".getBytes(StandardCharsets.UTF_8);
                        assertThat(post(policy.host(), policy.formFields(), payload)).isBetween(200, 299);
                        storage.verifyObjectExists("smoke-minio", policy.objectKey());
                        var get = HttpRequest.newBuilder(storage.signedViewUri("smoke-minio", policy.objectKey(), 60))
                                .timeout(Duration.ofSeconds(15)).GET().build();
                        var response = client().send(get, HttpResponse.BodyHandlers.ofByteArray());
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.body()).isEqualTo(payload);
                        var wrongOwner = new LinkedHashMap<>(policy.formFields());
                        wrongOwner.put("key", policy.objectKey().replace(owner, "another-owner"));
                        assertThat(post(policy.host(), wrongOwner, payload)).isBetween(400, 499);
                        var wrongMime = new LinkedHashMap<>(policy.formFields());
                        wrongMime.put("Content-Type", "image/png");
                        assertThat(post(policy.host(), wrongMime, payload)).isBetween(400, 499);
                        assertThatThrownBy(() -> storage.validateObjectOwner("github", "another-owner", policy.objectKey()))
                                .isInstanceOf(IllegalArgumentException.class);
                        var limited = storage.createUploadPolicy(owner, "file", "text/plain", "too-large.txt",
                                "github", 60, 60, 1);
                        assertThat(post(limited.host(), limited.formFields(), payload)).isBetween(400, 499);
                    } finally {
                        int slash = policy.objectKey().lastIndexOf('/');
                        files.delete(new FileInfo().setPlatform("smoke-minio").setBasePath("")
                                .setPath(policy.objectKey().substring(0, slash + 1))
                                .setFilename(policy.objectKey().substring(slash + 1)));
                    }
                });
    }

    private static int post(String host, Map<String, String> fields, byte[] bytes) throws Exception {
        String boundary = "xfs-smoke-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        for (var field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"smoke.txt\""
                + "\r\nContent-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(host)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return client().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " 未设置");
        return value;
    }
}
