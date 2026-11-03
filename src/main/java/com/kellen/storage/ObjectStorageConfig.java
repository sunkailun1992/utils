package com.kellen.storage;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.springframework.context.annotation.Configuration;

/** 对象存储只使用 X File Storage 的平台配置与客户端生命周期。 */
@Configuration(proxyBeanMethods = false)
@EnableFileStorage
public class ObjectStorageConfig {
}
