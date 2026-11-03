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
        Sms.signName = null;
        LiveStreaming.appName = null;
        LiveStreaming.pullDomain = null;
        LiveStreaming.pullKey = null;
        LiveStreaming.pushDomain = null;
        LiveStreaming.pushKey = null;
        DingDing.secret = null;
        DingDing.url = null;
    }

    @Test
    void shouldStartAllOptionalComponentsWithoutAnyAliyunProperties() {
        try (AnnotationConfigApplicationContext context = context(Map.of("ai.object-storage.provider", "MINIO"))) {
            assertThat(context.getBeansOfType(Oss.class)).hasSize(1);
            assertThat(context.getBeansOfType(Sms.class)).hasSize(1);
            assertThat(context.getBeansOfType(LiveStreaming.class)).hasSize(1);
            assertThat(context.getBeansOfType(DingDing.class)).hasSize(1);
            assertThat(AliyunKey.accessKeyId).isEmpty();
            assertThat(AliyunKey.accessKeySecret).isEmpty();
            assertThat(Oss.bucket).isEmpty();
            assertThat(Oss.endpoint).isEmpty();
            assertThat(Oss.host()).isEmpty();
            assertThat(Sms.signName).isEmpty();
            assertThat(LiveStreaming.appName).isEmpty();
            assertThat(LiveStreaming.pullDomain).isEmpty();
            assertThat(LiveStreaming.pullKey).isEmpty();
            assertThat(LiveStreaming.pushDomain).isEmpty();
            assertThat(LiveStreaming.pushKey).isEmpty();
            assertThat(DingDing.secret).isEmpty();
            assertThat(DingDing.url).isEmpty();
        }
    }

    @Test
    void shouldKeepConfiguredOptionalValues() {
        try (AnnotationConfigApplicationContext context = context(Map.of(
                "aliyun.oss.bucket", "fixture-bucket",
                "aliyun.oss.endpoint", "https://oss.example.invalid",
                "aliyun.sms.signName", "fixture-sign",
                "aliyun.liveStreaming.appName", "fixture-app",
                "aliyun.dingding.url", "https://notification.example.invalid"))) {
            assertThat(context.getBean(Oss.class)).isNotNull();
            assertThat(Oss.bucket).isEqualTo("fixture-bucket");
            assertThat(Oss.endpoint).isEqualTo("https://oss.example.invalid");
            assertThat(Sms.signName).isEqualTo("fixture-sign");
            assertThat(LiveStreaming.appName).isEqualTo("fixture-app");
            assertThat(DingDing.url).isEqualTo("https://notification.example.invalid");
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
        context.register(AliyunKey.class, Oss.class, Sms.class, LiveStreaming.class, DingDing.class);
        context.refresh();
        return context;
    }
}
