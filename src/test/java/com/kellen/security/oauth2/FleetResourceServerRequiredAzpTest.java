package com.kellen.security.oauth2;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ai-agent 的接法：受众 github-ai-agent，且只收 github-ai 经令牌交换换来的令牌（required-azp）。
 */
@SpringBootTest(
        classes = FleetResourceServerTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-ai-agent",
                "fleet.security.oauth2.required-azp=github-ai",
                "fleet.security.oauth2.context-cache-ttl=0s"
        }
)
class FleetResourceServerRequiredAzpTest {

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
        provider.enabledUser("u1", "t1", "d1", List.of());
    }

    @Test
    void exchangedTokenFromRequiredClientIsAccepted() throws Exception {
        String token = KEYCLOAK.token(claims -> claims.audience("github-ai-agent").claim("azp", "github-ai").claim("user_id", "u1"));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"));
    }

    @Test
    void tokenFromOtherClientIs403EvenWithRightAudience() throws Exception {
        String token = KEYCLOAK.token(claims -> claims.audience("github-ai-agent").claim("azp", "github-gateway").claim("user_id", "u1"));
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    void gatewayTokenWithApiAudienceIs401() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isUnauthorized());
    }
}
