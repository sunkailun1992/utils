package com.kellen.web;

import com.kellen.config.web.ReqConfig;
import com.kellen.security.UserContextHolder;
import com.kellen.security.oauth2.FleetResourceServerAutoConfiguration;
import com.kellen.security.oauth2.StubSecurityContextRpcService;
import com.kellen.security.oauth2.test.FakeIssuer;
import com.kellen.utils.context.TenantContextHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Tomcat、HTTP、MVC 与资源服务器链：租户只取认证上下文。旧的租户请求头不起作用，
 * {@code X-Tenant-Id} 只能在用户所属租户里选择，匿名请求不带租户上下文。
 */
@SpringBootTest(classes = TenantHttpIsolationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=tenant-http-isolated", "tenant.enabled=true", "tenant.ignore-without-tenant=false",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.context-cache-ttl=0s",
                "fleet.security.oauth2.permit-paths=/auth/sessions,/error"})
class TenantHttpIsolationTest {

    static final FakeIssuer KEYCLOAK = FakeIssuer.start();

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KEYCLOAK::issuer);
    }

    @AfterAll
    static void stopKeycloak() throws Exception {
        KEYCLOAK.close();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private StubSecurityContextRpcService provider;

    @BeforeEach
    void users() {
        provider.reset();
        provider.enabledUser("user-1", "100", "d1", List.of());
        provider.tenantlessUser("user-without-tenant");
    }

    @Test
    void trustedTenantWinsOverLegacyTenantHeaders() throws Exception {
        var response = send("/tenant-probe", KEYCLOAK.userToken("user-1"), false, false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("100/100");
    }

    @Test
    void selectingForeignTenantIsForbidden() throws Exception {
        assertThat(send("/tenant-probe", KEYCLOAK.userToken("user-1"), false, true).statusCode()).isEqualTo(403);
    }

    @Test
    void userWithoutTenantCannotUseHeadersToGainOne() throws Exception {
        assertThat(send("/tenant-probe", KEYCLOAK.userToken("user-without-tenant"), false, false).statusCode()).isEqualTo(403);
    }

    @Test
    void forgedHeadersWithoutAuthenticationCannotReachProtectedController() throws Exception {
        assertThat(send("/tenant-probe", null, false, true).statusCode()).isEqualTo(401);
    }

    @Test
    void publicLoginRemainsReachableWithoutAnAnonymousHeaderTenantContext() throws Exception {
        var response = send("/auth/sessions", null, true, true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("none");
    }

    private HttpResponse<String> send(String path, String token, boolean login, boolean selectTenant) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("tenantId", "200").header("tenant-id", "200");
        if (selectTenant) {
            request.header("X-Tenant-Id", "200");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (login) {
            request.POST(HttpRequest.BodyPublishers.ofString("{}"));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ReqConfig.class, ProbeController.class})
    @ImportAutoConfiguration({
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class,
            org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class,
            org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class,
            org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class,
            org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class,
            FleetResourceServerAutoConfiguration.class,
            org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration.class
    })
    static class Config {

        @Bean
        StubSecurityContextRpcService securityContextRpcService() {
            return new StubSecurityContextRpcService();
        }
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
