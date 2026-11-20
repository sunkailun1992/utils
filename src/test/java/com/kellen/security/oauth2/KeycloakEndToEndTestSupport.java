package com.kellen.security.oauth2;

import com.kellen.security.SecurityUser;
import com.kellen.security.oauth2.client.FleetOAuth2ClientAutoConfiguration;
import com.kellen.security.oauth2.client.FleetOAuth2Clients;
import jakarta.servlet.Filter;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对真实 Keycloak 的端到端场景（容器与 NAS 两个子类共用）：网关客户端登录拿用户令牌访问受保护接口；
 * 受众不对的令牌被拒；ai 用标准令牌交换换出 ai-agent 受众、同一用户的令牌。业务上下文用假的 user 服务提供方。
 */
@SpringBootTest(
        classes = KeycloakEndToEndTestSupport.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.context-cache-ttl=0s",
                "spring.security.oauth2.client.registration.github-ai-agent.provider=keycloak",
                "spring.security.oauth2.client.registration.github-ai-agent.client-id=github-ai",
                "spring.security.oauth2.client.registration.github-ai-agent.authorization-grant-type=urn:ietf:params:oauth:grant-type:token-exchange",
                "fleet.security.oauth2.token-exchange.github-ai-agent.audience=github-ai-agent"
        }
)
abstract class KeycloakEndToEndTestSupport {

    /**
     * 网关客户端登记的回调地址之一；登录只需要从它的跳转里取授权码，不需要真有服务在监听。
     */
    static final String REDIRECT_URI = "http://localhost:8000/login/oauth2/code/keycloak";

    static final MockWebServer DOWNSTREAM = new MockWebServer();

    static final List<String> DOWNSTREAM_AUTHORIZATION = new CopyOnWriteArrayList<>();

    static {
        DOWNSTREAM.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                DOWNSTREAM_AUTHORIZATION.add(String.valueOf(request.getHeaders().get("Authorization")));
                return new MockResponse.Builder().code(200).body("ok").build();
            }
        });
        try {
            DOWNSTREAM.start(InetAddress.getByName("127.0.0.1"), 0);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private StubSecurityContextRpcService provider;

    @Autowired
    private FleetOAuth2Clients clients;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    /**
     * 被测 Keycloak 与测试用户。
     *
     * @return 账号信息
     */
    protected abstract Account account();

    /**
     * 子类在 {@code @DynamicPropertySource} 里调用：签发方、令牌端点与 ai 客户端密钥。
     */
    static void register(DynamicPropertyRegistry registry, Supplier<String> issuer, Supplier<String> aiClientSecret) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer::get);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", () -> issuer.get() + "/protocol/openid-connect/token");
        registry.add("spring.security.oauth2.client.registration.github-ai-agent.client-secret", aiClientSecret::get);
    }

    @AfterAll
    static void stopDownstream() throws Exception {
        DOWNSTREAM.close();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        provider.reset();
        provider.enabledUser(account().userId(), "t1", "d1", List.of("a:b"));
        DOWNSTREAM_AUTHORIZATION.clear();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userTokenFromRealLoginReachesProtectedEndpoint() throws Exception {
        String token = login();
        Map<String, Object> claims = KeycloakBrowserlessLogin.claims(token);
        assertThat(claims).containsEntry("azp", "github-gateway").containsEntry("user_id", account().userId());
        assertThat(audiences(claims)).contains("github-api", "github-ai");
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FleetAuthenticationToken"))
                .andExpect(jsonPath("$.userId").value(account().userId()));
        assertThat(provider.calls()).containsExactly(account().userId() + "|null");
    }

    @Test
    void tokenExchangeIssuesAiAgentTokenForTheSameUser() throws Exception {
        Map<String, Object> claims = KeycloakBrowserlessLogin.claims(exchange(login()));
        assertThat(claims).containsEntry("azp", "github-ai").containsEntry("user_id", account().userId());
        assertThat(audiences(claims)).contains("github-ai-agent").doesNotContain("github-api");
    }

    @Test
    void tokenForAnotherAudienceIs401() throws Exception {
        String exchanged = exchange(login());
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + exchanged))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        Account account = account();
        return KeycloakBrowserlessLogin.accessToken(account.issuer(), "github-gateway", account.gatewayClientSecret(), REDIRECT_URI,
                account.username(), account.password());
    }

    private String exchange(String userToken) {
        SecurityUser user = SecurityUser.builder().userId(account().userId()).tenantId("t1").enabled(true).tenantIds(List.of("t1")).build();
        SecurityContextHolder.getContext().setAuthentication(new FleetAuthenticationToken(jwtDecoder.decode(userToken),
                SecurityContextResult.ok(user), List.of(), account().userId()));
        RestClient client = RestClient.builder().requestInterceptor(clients.interceptor("github-ai-agent")).build();
        client.get().uri(DOWNSTREAM.url("/agent").newBuilder().host("127.0.0.1").build().uri()).retrieve().body(String.class);
        return DOWNSTREAM_AUTHORIZATION.getLast().substring("Bearer ".length());
    }

    private static List<String> audiences(Map<String, Object> claims) {
        Object aud = claims.get("aud");
        return aud instanceof Collection<?> values ? values.stream().map(String::valueOf).toList() : List.of(String.valueOf(aud));
    }

    /**
     * 被测 Keycloak 与测试用户。
     *
     * @param issuer              签发方
     * @param username            用户名
     * @param password            口令
     * @param userId              用户属性 user_id
     * @param gatewayClientSecret 网关客户端密钥
     */
    record Account(String issuer, String username, String password, String userId, String gatewayClientSecret) {
    }

    /**
     * 测试应用：资源服务器与 OAuth2 客户端一起装，与 ai 服务的组合一致。
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
            OAuth2ClientAutoConfiguration.class,
            OAuth2ClientWebSecurityAutoConfiguration.class,
            FleetOAuth2ClientAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
    })
    @Import(FleetResourceServerTest.ProbeController.class)
    static class TestApplication {

        @Bean
        StubSecurityContextRpcService securityContextRpcService() {
            return new StubSecurityContextRpcService();
        }
    }
}
