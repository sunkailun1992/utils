package com.kellen.storage;

import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageConfigTest {
    @Test
    void boot4BindsOnlyTheSelectedXFileStoragePlatformWithoutLegacyAliyunSettings() {
        new ApplicationContextRunner().withUserConfiguration(ObjectStorageConfig.class)
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withPropertyValues("dromara.x-file-storage.default-platform=private-minio",
                        "dromara.x-file-storage.minio[0].platform=private-minio",
                        "dromara.x-file-storage.minio[0].enable-storage=true",
                        "dromara.x-file-storage.minio[0].end-point=https://storage.example.test",
                        "dromara.x-file-storage.minio[0].bucket-name=private-files",
                        "dromara.x-file-storage.minio[0].access-key=synthetic-access",
                        "dromara.x-file-storage.minio[0].secret-key=synthetic-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageService.class);
                    FileStorageService service = context.getBean(FileStorageService.class);
                    assertThat((Object) service.getFileStorage("private-minio")).isInstanceOf(MinioFileStorage.class);
                    assertThat(service.getProperties().getMinio().get(0).getBucketName()).isEqualTo("private-files");
                });
    }
}
