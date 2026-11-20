package com.kellen.security.oauth2;

import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.TenantContextHolder;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 标准 OAuth2 资源服务器与按请求加载的业务上下文：令牌校验走 Spring Boot 真实的 issuer 发现，
 * 业务上下文来自假的 user 服务提供方；本地缓存时长设为 0，每个请求都重新加载，便于逐条断言。
 */
@SpringBootTest(
        classes = FleetResourceServerTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.machine-clients=github-rag",
                "fleet.security.oauth2.context-cache-ttl=0s"
        }
)
@ExtendWith(OutputCaptureExtension.class)
class FleetResourceServerTest {

    static final TestKeycloak KEYCLOAK = TestKeycloak.start();

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KEYCLOAK::issuer);
    }

    @AfterAll
    static void stopKeycloak() throws Exception {
        KEYCLOAK.close();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private StubSecurityContextRpcService provider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        provider.reset();
    }

    @Test
    void missingTokenIs401() throws Exception {
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0220"));
    }

    @Test
    void wrongIssuerIs401() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        String token = KEYCLOAK.token(claims -> claims.issuer("http://127.0.0.1:1/realms/other").claim("user_id", "u1"));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0220"));
        assertThat(provider.calls()).isEmpty();
    }

    @Test
    void wrongAudienceIs401() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        String token = KEYCLOAK.token(claims -> claims.audience("account").claim("user_id", "u1"));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(provider.calls()).isEmpty();
    }

    @Test
    void expiredTokenIs401() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        Instant now = Instant.now();
        String token = KEYCLOAK.token(claims -> claims.claim("user_id", "u1")
                .issueTime(Date.from(now.minusSeconds(900))).expirationTime(Date.from(now.minusSeconds(600))));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hs256TokenIs401() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.hs256Token("u1")))
                .andExpect(status().isUnauthorized());
        assertThat(provider.calls()).isEmpty();
    }

    @Test
    void rs512TokenIs401EvenWhenKeyDoesNotPinAlgorithm() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.rs512Token("u1")))
                .andExpect(status().isUnauthorized());
        assertThat(provider.calls()).isEmpty();
    }

    @Test
    void validUserTokenPopulatesContexts() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of("a:b", "ROLE_admin"));
        String token = KEYCLOAK.userToken("u1");
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FleetAuthenticationToken"))
                .andExpect(jsonPath("$.name").value("u1"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.deptId").value("d1"))
                .andExpect(jsonPath("$.tenantId").value("t1"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_admin"))
                .andExpect(jsonPath("$.authorities[1]").value("a:b"));
        mockMvc.perform(get("/api/needs-ab").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(provider.calls()).containsOnly("u1|null");
        assertThat(UserContextHolder.get()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingAuthorityIs403() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of("other:perm"));
        mockMvc.perform(get("/api/needs-ab").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    void disabledUserIs403() throws Exception {
        provider.disabledUser("u2");
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    void unknownUserIs401() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("nobody")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0220"));
    }

    @Test
    void tokenWithoutUserIdFromUnknownClientIsNotTreatedAsUser() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.token(claims -> { })))
                .andExpect(status().isForbidden());
        assertThat(provider.calls()).isEmpty();
    }

    @Test
    void foreignTenantHeaderIs403() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1"))
                        .header("X-Tenant-Id", "t9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
        assertThat(provider.calls()).containsExactly("u1|t9");
    }

    @Test
    void ownSecondTenantHeaderSwitchesTenant() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of(), "t2");
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1"))
                        .header("X-Tenant-Id", "t2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("t2"));
    }

    @Test
    void machineTokenWithoutUserIsMachinePrincipal() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.machineToken("github-rag")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MachineAuthenticationToken"))
                .andExpect(jsonPath("$.name").value("github-rag"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
        assertThat(provider.calls()).isEmpty();
    }

    @Test
    void machineTokenNotInAllowlistIs403() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.machineToken("github-unknown")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    void permitPathsNeedNoToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void clockSkewWithinSixtySecondsAccepted() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        Instant now = Instant.now();
        String notYetValid = KEYCLOAK.token(claims -> claims.claim("user_id", "u1")
                .issueTime(Date.from(now.plusSeconds(30))).notBeforeTime(Date.from(now.plusSeconds(30))));
        String justExpired = KEYCLOAK.token(claims -> claims.claim("user_id", "u1")
                .issueTime(Date.from(now.minusSeconds(330))).expirationTime(Date.from(now.minusSeconds(30))));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + notYetValid)).andExpect(status().isOk());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + justExpired)).andExpect(status().isOk());
    }

    @Test
    void clockSkewBeyondSixtySecondsRejected() throws Exception {
        provider.enabledUser("u1", "t1", "d1", List.of());
        Instant now = Instant.now();
        String expired = KEYCLOAK.token(claims -> claims.claim("user_id", "u1")
                .issueTime(Date.from(now.minusSeconds(420))).expirationTime(Date.from(now.minusSeconds(120))));
        String notYetValid = KEYCLOAK.token(claims -> claims.claim("user_id", "u1")
                .notBeforeTime(Date.from(now.plusSeconds(120))));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + notYetValid)).andExpect(status().isUnauthorized());
    }

    @Test
    void providerFailureIs503WithoutStackTrace(CapturedOutput output) throws Exception {
        provider.failWith(new IllegalStateException("No provider available for the service SecurityContextRpcService"));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(output.getAll()).contains("SecurityContextRpcService").doesNotContain("\tat ");
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void responsesNeverEchoTheToken() throws Exception {
        String token = KEYCLOAK.userToken("nobody");
        String body = mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(token);
    }

    /**
     * 测试应用：只装 Web MVC、Spring Security、Boot 资源服务器与 utils 的资源服务器自动配置。
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration({
            PropertyPlaceholderAutoConfiguration.class,
            ConfigurationPropertiesAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            FleetResourceServerAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
    })
    @Import(ProbeController.class)
    static class TestApplication {

        @Bean
        StubSecurityContextRpcService securityContextRpcService() {
            return new StubSecurityContextRpcService();
        }
    }

    /**
     * 探针接口：把处理器里读到的认证与上下文原样返回。
     */
    @RestController
    static class ProbeController {

        @GetMapping("/api/probe")
        Map<String, Object> probe() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            SecurityUser user = UserContextHolder.get();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("type", authentication.getClass().getSimpleName());
            view.put("name", authentication.getName());
            if (user != null) {
                view.put("userId", user.getUserId());
                view.put("deptId", user.getDeptId());
            }
            if (TenantContextHolder.getTenantId() != null) {
                view.put("tenantId", TenantContextHolder.getTenantId());
            }
            view.put("authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
            return view;
        }

        @PreAuthorize("hasAuthority('a:b')")
        @GetMapping("/api/needs-ab")
        String needsAb() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @GetMapping("/v3/api-docs")
        String apiDocs() {
            return "{}";
        }
    }
}
