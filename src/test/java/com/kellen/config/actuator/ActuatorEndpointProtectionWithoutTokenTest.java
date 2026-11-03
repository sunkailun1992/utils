package com.kellen.config.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证服务没有配置 Actuator 令牌时失败关闭：除 health 外的端点一律拒绝。
 */
@SpringBootTest(
        classes = ActuatorProtectionTestApplication.class,
        properties = "management.endpoints.web.exposure.include=*"
)
class ActuatorEndpointProtectionWithoutTokenTest extends ActuatorProtectionTestSupport {

    /**
     * 未配置令牌时，任意请求头取值都不能打开 env。
     */
    @Test
    void envEndpointRejectsAnyTokenWhenNoTokenConfigured() throws Exception {
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, "any-value"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 未配置令牌时，空请求头不能与空配置值匹配成功。
     */
    @Test
    void envEndpointRejectsBlankTokenWhenNoTokenConfigured() throws Exception {
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, ""))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 未配置令牌不影响探活。
     */
    @Test
    void healthEndpointStaysPublicWhenNoTokenConfigured() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
