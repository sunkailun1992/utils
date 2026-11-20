package com.kellen.security.oauth2.client;

import com.kellen.security.SecurityUser;
import com.kellen.security.oauth2.FleetAuthenticationToken;
import com.kellen.security.oauth2.FleetResourceServerAutoConfiguration;
import com.kellen.security.oauth2.SecurityContextResult;
import com.kellen.security.oauth2.SecurityContextRpcService;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出站 OAuth2：客户端凭证与 RFC 8693 令牌交换。同一个本地 MockWebServer 充当 Keycloak 令牌端点与下游服务。
 * 测试上下文同时装资源服务器与 OAuth2 客户端，和 ai 服务的实际组合一致。
 */
@SpringBootTest(
        classes = FleetOAuth2ClientTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "spring.security.oauth2.client.registration.github-rag-engine.provider=keycloak",
                "spring.security.oauth2.client.registration.github-rag-engine.client-id=github-rag",
                "spring.security.oauth2.client.registration.github-rag-engine.client-secret=rag-test-secret",
                "spring.security.oauth2.client.registration.github-rag-engine.authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.registration.github-ai-agent.provider=keycloak",
                "spring.security.oauth2.client.registration.github-ai-agent.client-id=github-ai",
                "spring.security.oauth2.client.registration.github-ai-agent.client-secret=ai-test-secret",
                "spring.security.oauth2.client.registration.github-ai-agent.authorization-grant-type=urn:ietf:params:oauth:grant-type:token-exchange",
                "fleet.security.oauth2.token-exchange.github-ai-agent.audience=github-ai-agent"
        }
)
class FleetOAuth2ClientTest {

    static final MockWebServer SERVER = new MockWebServer();

    static final List<Map<String, String>> TOKEN_REQUESTS = new CopyOnWriteArrayList<>();

    static final List<String> DOWNSTREAM_AUTHORIZATION = new CopyOnWriteArrayList<>();

    static final AtomicInteger ISSUED = new AtomicInteger();

    static volatile int expiresIn = 300;

    static volatile boolean rejectTokenRequests;

    static {
        SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getUrl().encodedPath();
                if (path.endsWith("/protocol/openid-connect/token")) {
                    Map<String, String> form = form(request.getBody() == null ? "" : request.getBody().utf8());
                    form.put("authorization", request.getHeaders().get("Authorization"));
                    TOKEN_REQUESTS.add(form);
                    if (rejectTokenRequests) {
                        return new MockResponse.Builder().code(400).addHeader("Content-Type", "application/json")
                                .body("{\"error\":\"invalid_request\",\"error_description\":\"rejected\"}").build();
                    }
                    String prefix = form.get("grant_type").endsWith("token-exchange") ? "exchanged-" : "cc-";
                    return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                            .body("{\"access_token\":\"" + prefix + ISSUED.incrementAndGet() + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                                    + expiresIn + "}").build();
                }
                if (path.equals("/downstream")) {
                    DOWNSTREAM_AUTHORIZATION.add(String.valueOf(request.getHeaders().get("Authorization")));
                    return new MockResponse.Builder().code(200).body("ok").build();
                }
                return new MockResponse.Builder().code(404).build();
            }
        });
        try {
            SERVER.start(InetAddress.getByName("127.0.0.1"), 0);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static final String KEYCLOAK = SERVER.url("/realms/github-dev").newBuilder().host("127.0.0.1").build().toString();

    static final String DOWNSTREAM = SERVER.url("/downstream").newBuilder().host("127.0.0.1").build().toString();

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> KEYCLOAK);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", () -> KEYCLOAK + "/protocol/openid-connect/token");
    }

    @AfterAll
    static void stop() throws Exception {
        SERVER.close();
    }

    @Autowired
    private FleetOAuth2Clients clients;

    @Autowired
    private List<SecurityFilterChain> chains;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClients;

    @BeforeEach
    void reset() {
        authorizedClients.removeAuthorizedClient("github-rag-engine", FleetOAuth2Clients.clientPrincipalName("github-rag-engine"));
        TOKEN_REQUESTS.clear();
        DOWNSTREAM_AUTHORIZATION.clear();
        expiresIn = 300;
        rejectTokenRequests = false;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientCredentialsAttachesBearer() {
        String token = callWithRestClient("github-rag-engine");
        assertThat(TOKEN_REQUESTS).hasSize(1);
        Map<String, String> request = TOKEN_REQUESTS.getFirst();
        assertThat(request).containsEntry("grant_type", "client_credentials");
        assertThat(request.get("authorization")).isEqualTo(basic("github-rag", "rag-test-secret"));
        assertThat(DOWNSTREAM_AUTHORIZATION).containsExactly("Bearer " + token);
        assertThat(token).startsWith("cc-");
    }

    @Test
    void clientCredentialsTokenIsSharedAcrossUsers() {
        signIn("u1", "subject-u1");
        callWithRestClient("github-rag-engine");
        signIn("u2", "subject-u2");
        callWithRestClient("github-rag-engine");
        SecurityContextHolder.clearContext();
        callWithRestClient("github-rag-engine");
        assertThat(TOKEN_REQUESTS).hasSize(1);
    }

    @Test
    void tokenExchangeSendsSubjectTokenAndAudience() {
        signIn("u-exchange", "subject-token-S");
        String token = callWithRestClient("github-ai-agent");
        assertThat(TOKEN_REQUESTS).hasSize(1);
        Map<String, String> request = TOKEN_REQUESTS.getFirst();
        assertThat(request).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                .containsEntry("subject_token", "subject-token-S")
                .containsEntry("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                .containsEntry("requested_token_type", "urn:ietf:params:oauth:token-type:access_token")
                .containsEntry("audience", "github-ai-agent");
        assertThat(request.get("authorization")).isEqualTo(basic("github-ai", "ai-test-secret"));
        assertThat(token).startsWith("exchanged-");
        assertThat(DOWNSTREAM_AUTHORIZATION).containsExactly("Bearer " + token);
    }

    @Test
    void tokenExchangeWithoutUserFails() {
        RestClient client = RestClient.builder().requestInterceptor(clients.interceptor("github-ai-agent")).build();
        assertThatThrownBy(() -> client.get().uri(DOWNSTREAM).retrieve().body(String.class)).isInstanceOf(RuntimeException.class);
        assertThat(TOKEN_REQUESTS).isEmpty();
        assertThat(DOWNSTREAM_AUTHORIZATION).isEmpty();
    }

    @Test
    void tokenEndpointRejectionNeverCallsDownstream() {
        rejectTokenRequests = true;
        signIn("u-rejected", "subject-rejected");
        RestClient client = RestClient.builder().requestInterceptor(clients.interceptor("github-ai-agent")).build();
        assertThatThrownBy(() -> client.get().uri(DOWNSTREAM).retrieve().body(String.class)).isInstanceOf(RuntimeException.class);
        assertThat(TOKEN_REQUESTS).hasSize(1);
        assertThat(DOWNSTREAM_AUTHORIZATION).isEmpty();
    }

    @Test
    void tokensAreCachedUntilExpiry() {
        callWithRestClient("github-rag-engine");
        callWithRestClient("github-rag-engine");
        assertThat(TOKEN_REQUESTS).hasSize(1);
        signIn("u-cached", "subject-cached");
        callWithRestClient("github-ai-agent");
        callWithRestClient("github-ai-agent");
        assertThat(TOKEN_REQUESTS).hasSize(2);
    }

    @Test
    void tokensInsideClockSkewAreRefreshed() {
        expiresIn = 30;
        signIn("u-short", "subject-short");
        callWithRestClient("github-ai-agent");
        callWithRestClient("github-ai-agent");
        assertThat(TOKEN_REQUESTS).hasSize(2);
    }

    @Test
    void webClientFilterExchangesForCurrentUser() {
        signIn("u-webclient", "subject-webclient");
        WebClient client = WebClient.builder().filter(clients.webClientFilter("github-ai-agent")).build();
        String body = client.get().uri(DOWNSTREAM).retrieve().bodyToMono(String.class).block();
        assertThat(body).isEqualTo("ok");
        assertThat(TOKEN_REQUESTS).singleElement().satisfies(request ->
                assertThat(request).containsEntry("subject_token", "subject-webclient").containsEntry("audience", "github-ai-agent"));
        assertThat(DOWNSTREAM_AUTHORIZATION).singleElement().asString().startsWith("Bearer exchanged-");
    }

    @Test
    void webClientFilterBlocksCallWithoutUser() {
        WebClient client = WebClient.builder().filter(clients.webClientFilter("github-ai-agent")).build();
        assertThatThrownBy(() -> client.get().uri(DOWNSTREAM).retrieve().bodyToMono(String.class).block())
                .isInstanceOf(RuntimeException.class);
        assertThat(DOWNSTREAM_AUTHORIZATION).isEmpty();
    }

    @Test
    void webClientFilterUsesClientCredentials() {
        WebClient client = WebClient.builder().filter(clients.webClientFilter("github-rag-engine")).build();
        client.get().uri(DOWNSTREAM).retrieve().bodyToMono(String.class).block();
        assertThat(DOWNSTREAM_AUTHORIZATION).singleElement().asString().startsWith("Bearer cc-");
    }

    @Test
    void unknownRegistrationFailsFast() {
        assertThatThrownBy(() -> clients.interceptor("github-missing")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resourceServerAndClientTogetherKeepOneApiChain() {
        assertThat(chains).hasSize(1);
    }

    private String callWithRestClient(String registrationId) {
        RestClient client = RestClient.builder().requestInterceptor(clients.interceptor(registrationId)).build();
        assertThat(client.get().uri(DOWNSTREAM).retrieve().body(String.class)).isEqualTo("ok");
        String last = DOWNSTREAM_AUTHORIZATION.getLast();
        return last.substring("Bearer ".length());
    }

    private static void signIn(String userId, String tokenValue) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "RS256").claim("user_id", userId).claim("azp", "github-gateway")
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        SecurityUser user = SecurityUser.builder().userId(userId).tenantId("t1").enabled(true).tenantIds(List.of("t1")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new FleetAuthenticationToken(jwt, SecurityContextResult.ok(user), List.of(), userId));
    }

    private static String basic(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
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
    static class TestApplication {

        @Bean
        SecurityContextRpcService securityContextRpcService() {
            return (userId, tenantId) -> SecurityContextResult.of(SecurityContextResult.Status.USER_NOT_FOUND);
        }
    }
}
