package com.kellen.security.oauth2;

import com.kellen.security.oauth2.test.FakeIssuer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 没有配置 {@code spring.security.oauth2.resourceserver.jwt.audiences} 的服务只收受众含 {@code github-api} 的令牌（设计：受众默认
 * github-api，可按服务改）。漏配不能让服务接受本域发给别的服务的令牌，例如 ai 手里那张给 ai-agent 的交换令牌。
 */
@SpringBootTest(
        classes = FleetResourceServerDefaultAudienceTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "fleet.security.oauth2.context-cache-ttl=0s"
        }
)
class FleetResourceServerDefaultAudienceTest {

    static final FakeIssuer KEYCLOAK = FakeIssuer.start();

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
        provider.enabledUser("u1", "t1", "d1", List.of());
    }

    @Test
    void tokenForTheDefaultAudienceIsAccepted() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isOk());
    }

    @Test
    void tokenIssuedForAnotherServiceIsRejectedWhenNoAudienceIsConfigured() throws Exception {
        String agentToken = KEYCLOAK.token(claims -> claims.audience("github-ai-agent").claim("user_id", "u1"));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isUnauthorized());
        assertThat(provider.calls()).isEmpty();
    }

    /**
     * 测试应用：同 {@link FleetResourceServerTest} 的装配，只是不配受众。
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
    @Import(FleetResourceServerTest.ProbeController.class)
    static class TestApplication {

        @Bean
        StubSecurityContextRpcService securityContextRpcService() {
            return new StubSecurityContextRpcService();
        }
    }
}
