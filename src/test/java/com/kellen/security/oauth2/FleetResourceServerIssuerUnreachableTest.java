package com.kellen.security.oauth2;

import com.kellen.security.oauth2.test.FakeIssuer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keycloak 不可达（NAS 链路断）：服务照常启动，受保护接口返回 401、不放行、日志不打印堆栈；恢复后无需重启。
 */
@SpringBootTest(
        classes = FleetResourceServerTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.context-cache-ttl=0s"
        }
)
@ExtendWith(OutputCaptureExtension.class)
class FleetResourceServerIssuerUnreachableTest {

    static final FakeIssuer KEYCLOAK = FakeIssuer.start();

    static final String UNREACHABLE_ISSUER = "http://127.0.0.1:" + closedPort() + FakeIssuer.REALM_PATH;

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> UNREACHABLE_ISSUER);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private StubSecurityContextRpcService provider;

    @Test
    void startsWhenIssuerUnreachableAndRejectsRequests(CapturedOutput output) throws Exception {
        provider.reset();
        provider.enabledUser("u1", "t1", "d1", List.of("a:b"));
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String token = KEYCLOAK.token(claims -> claims.issuer(UNREACHABLE_ISSUER).claim("user_id", "u1"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A0220"));
        }
        assertThat(provider.calls()).isEmpty();
        assertThat(output.getAll()).doesNotContain("\tat ").doesNotContain(token);
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
