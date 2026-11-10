package com.kellen.storage;

import cn.hutool.json.JSONUtil;
import com.qcloud.cos.auth.COSSigner;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.TencentCosFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用真实 COSSigner 验证 POST 合同；平台只替换网络边界，不接真实腾讯云。 */
class TencentCosPrivateStorageTest {
    private TencentCosFileStorage platform;
    private PrivateObjectStorageService storage;

    @BeforeEach void initialize() {
        var config = new FileStorageProperties.TencentCosConfig();
        config.setPlatform("private-cos"); config.setRegion("ap-singapore");
        config.setBucketName("synthetic-1250000000");
        config.setSecretId("synthetic-access"); config.setSecretKey("synthetic-secret");
        var properties = new FileStorageProperties(); properties.setDefaultPlatform("private-cos");
        properties.setTencentCos(List.of(config));
        platform = mock(TencentCosFileStorage.class);
        when(platform.getPlatform()).thenReturn("private-cos");
        when(platform.getBasePath()).thenReturn("");
        when(platform.getBucketName()).thenReturn("synthetic-1250000000");
        var files = new FileStorageService(); files.setSelf(files); files.setProperties(properties);
        files.setFileStorageList(new CopyOnWriteArrayList<>(List.of(platform)));
        files.setAspectList(new CopyOnWriteArrayList<>());
        storage = new PrivateObjectStorageService(files);
    }

    @Test void cosIsAConfiguredPrivatePlatformAndPostPolicyPinsOwnerMimeSizeAndExpiry() {
        assertThat(storage.configured()).isTrue();
        var policy = storage.createUploadPolicy("tenant-1/user-2", "image", "image/png", "photo.png",
                "github", 60, 1800, 1024);
        assertThat(policy.host()).isEqualTo("https://synthetic-1250000000.cos.ap-singapore.myqcloud.com");
        assertThat(policy.objectKey()).startsWith("github/tenant-1/user-2/image/");
        var fields = policy.formFields();
        assertThat(fields).containsEntry("key", policy.objectKey()).containsEntry("Content-Type", "image/png")
                .containsEntry("q-ak", "synthetic-access").containsEntry("q-sign-algorithm", "sha1");
        String raw = new String(Base64.getDecoder().decode(fields.get("policy")), StandardCharsets.UTF_8);
        assertThat(raw).contains(policy.objectKey(), "content-length-range", "1024", "image/png", "q-sign-time");
        assertThat(JSONUtil.parseObj(raw).getJSONArray("conditions")).isNotEmpty();
        assertThat(fields.get("q-signature")).isEqualTo(new COSSigner().buildPostObjectSignature(
                "synthetic-secret", fields.get("q-key-time"), raw));
        assertThat(fields.toString()).doesNotContain("synthetic-secret");
        storage.validateObjectOwner("github", "tenant-1/user-2", policy.objectKey());
        assertThatThrownBy(() -> storage.validateObjectOwner("github", "tenant-1/user-3", policy.objectKey()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void existenceCheckUsesOriginalPlatformAndExactKey() {
        when(platform.exists(any())).thenAnswer(invocation -> {
            FileInfo value = invocation.getArgument(0);
            assertThat(value.getPlatform()).isEqualTo("private-cos");
            assertThat(value.getPath()).isEqualTo("github/user-2/");
            assertThat(value.getFilename()).isEqualTo("file.png");
            return true;
        });
        storage.verifyObjectExists("private-cos", "github/user-2/file.png");
        verify(platform).exists(any());
    }

    @Test void nonemptyPlatformBasePathFailsBeforeAnyNetworkOperation() {
        when(platform.getBasePath()).thenReturn("wrong/");
        assertThatThrownBy(() -> storage.verifyObjectExists("private-cos", "github/file.png"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base-path");
        verify(platform, never()).exists(any());
    }
}
