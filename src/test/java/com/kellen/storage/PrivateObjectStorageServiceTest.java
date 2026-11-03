package com.kellen.storage;

import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.credentials.Credentials;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrivateObjectStorageServiceTest {
    private final MinioClient client = mock(MinioClient.class);
    private final MinioFileStorage platform = mock(MinioFileStorage.class);
    private FileStorageService files;
    private PrivateObjectStorageService storage;

    @BeforeEach
    void setUp() {
        var minio = new FileStorageProperties.MinioConfig();
        minio.setPlatform("private-minio");
        minio.setEndPoint("https://storage.example.test");
        minio.setBucketName("private-files");
        var properties = new FileStorageProperties();
        properties.setDefaultPlatform("private-minio");
        properties.setMinio(List.of(minio));
        files = new FileStorageService();
        files.setSelf(files);
        files.setProperties(properties);
        files.setFileStorageList(new CopyOnWriteArrayList<>(List.of(platform)));
        files.setAspectList(new CopyOnWriteArrayList<>());
        when(platform.getPlatform()).thenReturn("private-minio");
        when(platform.getBasePath()).thenReturn("");
        when(platform.getBucketName()).thenReturn("private-files");
        when(platform.getClient()).thenReturn(client);
        storage = new PrivateObjectStorageService(files);
    }

    @Test
    void minioPostPolicyPinsExactKeyMimeAndSizeAndKeepsOwnerIsolation() throws Exception {
        when(client.getPresignedPostFormData(any())).thenAnswer(invocation -> {
            PostPolicy policy = invocation.getArgument(0);
            return policy.formData(new Credentials("synthetic-access", "synthetic-secret", null, null), "us-east-1");
        });
        var policy = storage.createUploadPolicy("tenant-1/user-2", "image", "image/png", "../logo.png",
                "github", 300, 300, 1024);
        assertThat(policy.objectKey()).startsWith("github/tenant-1/user-2/image/").endsWith(".png");
        assertThat(policy.host()).isEqualTo("https://storage.example.test/private-files");
        assertThat(policy.formFields()).containsEntry("key", policy.objectKey()).containsEntry("Content-Type", "image/png")
                .containsKey("x-amz-signature");
        String decoded = new String(Base64.getDecoder().decode(policy.policy()), StandardCharsets.UTF_8);
        assertThat(decoded).contains(policy.objectKey(), "content-length-range", "1024", "image/png");
        storage.validateObjectOwner("github", "tenant-1/user-2", policy.objectKey());
        assertThatThrownBy(() -> storage.validateObjectOwner("github", "tenant-1/user-3", policy.objectKey()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existenceCheckUsesStoredPlatformAndExactObjectKey() {
        when(platform.exists(any())).thenReturn(true);
        storage.verifyObjectExists("private-minio", "github/user-1/file.png");
        var file = ArgumentCaptor.forClass(FileInfo.class);
        verify(platform).exists(file.capture());
        assertThat(file.getValue().getPath()).isEqualTo("github/user-1/");
        assertThat(file.getValue().getFilename()).isEqualTo("file.png");
        assertThat(file.getValue().getPlatform()).isEqualTo("private-minio");
    }

    @Test
    void unknownPlatformTraversalAndNonemptyBasePathFailBeforeSdkCalls() {
        assertThatThrownBy(() -> storage.verifyObjectExists("unknown", "github/file.png"))
                .isInstanceOf(IllegalStateException.class);
        for (String key : List.of("../file", "/file", "github/../file", "github\\file", "github//file", "file\n")) {
            assertThatThrownBy(() -> storage.verifyObjectExists(key)).isInstanceOf(IllegalArgumentException.class);
        }
        when(platform.getBasePath()).thenReturn("duplicated/");
        assertThatThrownBy(() -> storage.verifyObjectExists("github/file.png"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(client);
    }
}
