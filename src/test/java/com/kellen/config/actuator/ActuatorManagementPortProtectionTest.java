package com.kellen.config.actuator;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.tomcat.autoconfigure.actuate.web.server.TomcatServletManagementContextAutoConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证服务在 Nacos 配置独立管理端口时，管理端口上的 Actuator 同样需要令牌。
 *
 * <p>独立管理端口由 Spring Boot 在子容器里另起一个 Tomcat，只有 {@code springSecurityFilterChain}
 * 会被桥接过去；这里用真实端口和 HTTP 请求确认保护没有在子容器里丢失。</p>
 */
@SpringBootTest(
        classes = {ActuatorProtectionTestApplication.class, ActuatorManagementPortProtectionTest.EmbeddedServerConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoints.web.exposure.include=*",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorManagementPortProtectionTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @MockitoBean
    NacosAutoServiceRegistration serviceRegistration;

    @LocalManagementPort
    private int managementPort;

    /**
     * 管理端口上未带令牌读取 env 必须被拒绝。
     */
    @Test
    void envOnManagementPortRejectsRequestWithoutToken() throws Exception {
        assertThat(get("/actuator/env", null).statusCode()).isEqualTo(401);
    }

    /**
     * 管理端口上携带正确令牌仍能读取 env。
     */
    @Test
    void envOnManagementPortAllowsConfiguredToken() throws Exception {
        assertThat(get("/actuator/env", ActuatorProtectionTestSupport.TEST_TOKEN).statusCode()).isEqualTo(200);
    }

    /**
     * 管理端口上的 health 保持公开，供独立端口探活。
     */
    @Test
    void healthOnManagementPortStaysPublic() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + path)).GET();
        if (token != null) {
            request.header(ActuatorProtectionTestSupport.TOKEN_HEADER, token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 补充真实 Tomcat、管理端口子容器工厂和 Security 过滤器注册，和消费者启动时的完整自动配置保持一致。
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            TomcatServletWebServerAutoConfiguration.class,
            TomcatServletManagementContextAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    static class EmbeddedServerConfiguration {
    }
}
