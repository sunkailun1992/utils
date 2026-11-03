package com.kellen.config.actuator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Nacos 刷新改动令牌后无需重启即可生效，包括新增、轮换和删除。
 *
 * <p>Nacos 刷新会替换 {@link ConfigurableEnvironment} 中对应的属性源；这里直接增删一个属性源模拟刷新结果。
 * 负责人在事故处理中删除令牌配置时，旧令牌必须立即失效，不能等到重启。</p>
 */
@SpringBootTest(
        classes = ActuatorProtectionTestApplication.class,
        properties = "management.endpoints.web.exposure.include=*"
)
@DirtiesContext
class ActuatorTokenRefreshTest extends ActuatorProtectionTestSupport {

    private static final String REFRESHED_SOURCE = "simulated-nacos-refresh";

    @Autowired
    private ConfigurableEnvironment environment;

    @AfterEach
    void removeRefreshedSource() {
        environment.getPropertySources().remove(REFRESHED_SOURCE);
    }

    /**
     * 启动时未配置令牌，刷新补上令牌后立即可用。
     */
    @Test
    void tokenAddedByRefreshTakesEffectWithoutRestart() throws Exception {
        refreshToken("refreshed-token");

        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, "refreshed-token"))
                .andExpect(status().isOk());
    }

    /**
     * 刷新为新令牌后，新令牌可用、旧令牌立即失效。
     */
    @Test
    void rotatedTokenReplacesPreviousTokenWithoutRestart() throws Exception {
        refreshToken("first-token");
        refreshToken("second-token");

        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, "second-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, "first-token"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 刷新删除令牌配置后，原令牌立即失效。
     */
    @Test
    void tokenRemovedByRefreshIsRevokedWithoutRestart() throws Exception {
        refreshToken("revoked-token");
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, "revoked-token"))
                .andExpect(status().isOk());

        environment.getPropertySources().remove(REFRESHED_SOURCE);

        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, "revoked-token"))
                .andExpect(status().isUnauthorized());
    }

    private void refreshToken(String token) {
        environment.getPropertySources().remove(REFRESHED_SOURCE);
        environment.getPropertySources().addFirst(new MapPropertySource(REFRESHED_SOURCE, Map.of("security.actuator.token", token)));
    }
}
