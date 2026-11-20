package com.kellen.config.actuator;

import com.kellen.security.oauth2.FleetResourceServerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接入资源服务器的服务：Actuator 端点仍只认运维令牌，访问令牌与伪造的网关身份头都不能替代；业务路径由资源服务器链要求访问令牌。
 * issuer-uri 指向不可达地址：这些请求都不需要取公钥，延迟发现不会被触发。
 */
@SpringBootTest(
        classes = {ActuatorProtectionTestApplication.class, ActuatorEndpointProtectionWithResourceServerTest.ResourceServer.class},
        properties = {
                "management.endpoints.web.exposure.include=*",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/realms/github-dev",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorEndpointProtectionWithResourceServerTest extends ActuatorProtectionTestSupport {

    @Test
    void forgedGatewayIdentityCannotReadEnv() throws Exception {
        mockMvc.perform(get("/actuator/env")
                        .header("X-User-Id", "1")
                        .header("X-Tenant-Id", "1")
                        .header("X-Permissions", "*"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenCannotReplaceActuatorToken() throws Exception {
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer any-value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorTokenStillWorks() throws Exception {
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, TEST_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void businessPathsRequireAccessToken() throws Exception {
        mockMvc.perform(get("/orders").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({FleetResourceServerAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
    static class ResourceServer {
    }
}
