package com.kellen.config.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证开启统一认证的服务里，Actuator 不再借公开白名单或网关透传身份放行。
 *
 * <p>内网直连实例端口时调用方可以自行构造 {@code X-User-Id} 等网关透传头，
 * 这些头不能替代 Actuator 令牌。</p>
 */
@SpringBootTest(
        classes = ActuatorProtectionTestApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "security.auth.enabled=true",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorEndpointProtectionWithAuthEnabledTest extends ActuatorProtectionTestSupport {

    /**
     * 伪造的网关身份头不能打开 env。
     */
    @Test
    void forgedGatewayIdentityCannotReadEnv() throws Exception {
        mockMvc.perform(get("/actuator/env")
                        .header("X-User-Id", "1")
                        .header("X-Tenant-Id", "1")
                        .header("X-Permissions", "*"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 开启认证后 health 仍然公开，保证探活不受统一认证影响。
     */
    @Test
    void healthEndpointStaysPublicWhenAuthEnabled() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
