package com.kellen.aliyun;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用严格占位符解析验证 MinIO-only 场景无需提供未启用的阿里云配置。
 */
class OptionalAliyunConfigurationTest {

    @AfterEach
    void clearLegacyStaticConfiguration() {
        AliyunKey.accessKeyId = null;
        AliyunKey.accessKeySecret = null;
        Oss.bucket = null;
        Oss.endpoint = null;
        Oss.domain = null;
    }

    @Test
    void shouldStartAllOptionalComponentsWithoutAnyAliyunProperties() {
        try (AnnotationConfigApplicationContext context = context(Map.of("ai.object-storage.provider", "MINIO"))) {
            assertThat(context.getBeansOfType(Oss.class)).hasSize(1);
            assertThat(AliyunKey.accessKeyId).isEmpty();
            assertThat(AliyunKey.accessKeySecret).isEmpty();
            assertThat(Oss.bucket).isEmpty();
            assertThat(Oss.endpoint).isEmpty();
            assertThat(Oss.host()).isEmpty();
        }
    }

    @Test
    void shouldKeepConfiguredOptionalValues() {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "aliyun.oss.bucket", "fixture-bucket",
                "aliyun.oss.endpoint", "https://oss.example.invalid"))) {
            assertThat(context.getBean(Oss.class)).isNotNull();
            assertThat(Oss.bucket).isEqualTo("fixture-bucket");
            assertThat(Oss.endpoint).isEqualTo("https://oss.example.invalid");
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> values) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", values));
        context.registerBean(PropertySourcesPlaceholderConfigurer.class, () -> {
            PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
            configurer.setIgnoreUnresolvablePlaceholders(false);
            return configurer;
        });
        context.register(AliyunKey.class, Oss.class);
        context.refresh();
        return context;
    }
}
