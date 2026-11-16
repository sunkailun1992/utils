package com.kellen.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 上传下载统一走 X File Storage：阿里云 OSS 专用的静态配置与直传工具已删除，三家 SDK 只作统一存储的驱动。 */
class OssSpecificCodeRemovedTest {

    @Test
    void ossOnlyClassesAreGone() {
        for (String name : List.of("com.kellen.aliyun.Oss", "com.kellen.aliyun.AliyunKey",
                "com.kellen.aliyun.oss.token.OssTokenProperties", "com.kellen.aliyun.oss.token.OssTokenUtils",
                "com.kellen.aliyun.oss.token.OssUploadPolicy")) {
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void vendorSdksStayAsUnifiedStorageDrivers() {
        for (String name : List.of("org.dromara.x.file.storage.core.platform.AliyunOssFileStorage",
                "org.dromara.x.file.storage.core.platform.MinioFileStorage",
                "org.dromara.x.file.storage.core.platform.TencentCosFileStorage",
                "com.aliyun.oss.OSS", "io.minio.MinioClient", "com.qcloud.cos.COSClient")) {
            assertThatCode(() -> Class.forName(name)).as(name).doesNotThrowAnyException();
        }
    }
}
