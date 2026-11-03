package com.kellen.web;

import com.kellen.config.web.ReqConfig;
import com.kellen.security.config.SecurityAuthConfig;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.auth.JwtKeyConfig;
import com.kellen.utils.auth.JwtUtils;
import com.kellen.utils.context.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实签名 JWT 和 HTTP/MVC/Security 链验证租户头不能替换认证租户。 */
@SpringBootTest(classes = TenantHttpIsolationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=tenant-http-isolated", "security.auth.enabled=true",
                "security.auth.header-enabled=false", "tenant.enabled=true", "tenant.ignore-without-tenant=false"})
class TenantHttpIsolationTest {
    @LocalServerPort private int port;
    @Autowired private Environment environment;

    @DynamicPropertySource
    static void syntheticKey(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void signingEnvironment() {
        new JwtKeyConfig(environment);
    }

    @Test
    void trustedJwtTenantWinsOverEveryHttpTenantHeader() throws Exception {
        String token = JwtUtils.createJwt("tenant-jti", "user-1", Map.of("tenantId", "100"));
        var response = send("/tenant-probe", token, false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("100/100");
    }

    @Test
    void tokenWithoutTenantCannotUseSpoofedHeadersToGainOne() throws Exception {
        String token = JwtUtils.createJwt("tenantless-jti", "user-1", Map.of());
        assertThat(send("/tenant-probe", token, false).statusCode()).isEqualTo(403);
    }

    @Test
    void forgedHeadersWithoutAuthenticationCannotReachProtectedController() throws Exception {
        assertThat(send("/tenant-probe", null, false).statusCode()).isIn(401, 403);
    }

    @Test
    void publicLoginRemainsReachableWithoutAnAnonymousHeaderTenantContext() throws Exception {
        var response = send("/auth/sessions", null, true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("none");
    }

    private HttpResponse<String> send(String path, String token, boolean login) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("tenantId", "200").header("tenant-id", "200").header("X-Tenant-Id", "200");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (login) request.POST(HttpRequest.BodyPublishers.ofString("{}"));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ReqConfig.class, SecurityAuthConfig.class, JwtKeyConfig.class, ProbeController.class})
    @ImportAutoConfiguration({
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class,
            org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class,
            org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class,
            org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class,
            org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class
    })
    static class Config {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/tenant-probe")
        String tenant() {
            return TenantContextHolder.getTenantId() + "/" + UserContextHolder.get().getTenantId();
        }

        @PostMapping("/auth/sessions")
        String login() {
            return TenantContextHolder.getTenantId() == null ? "none" : TenantContextHolder.getTenantId();
        }
    }
}
