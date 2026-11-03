package com.kellen.config.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证消费者为避开 {@link ConfigClientController} 启动连 Redis 而整包排除 {@code com.kellen.config.actuator} 时，
 * Actuator 保护不会跟着消失。
 *
 * <p>ai 服务的测试启动类就用正则排除了整个 actuator 包；统一认证关闭（多数服务的默认值）时，
 * 主安全链会放行所有请求，只能依靠 Actuator 专用安全链拦截。</p>
 */
@SpringBootTest(
        classes = ActuatorPackageExclusionTest.ActuatorPackageExcludedApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorPackageExclusionTest extends ActuatorProtectionTestSupport {

    /**
     * 排除 actuator 包后，未带令牌读取 env 仍必须被拒绝。
     */
    @Test
    void envEndpointStaysProtectedWhenActuatorPackageIsExcluded() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 与 ai 测试启动类相同，扫描公共配置但不包含 {@code com.kellen.config.actuator}。
     */
    @Configuration(proxyBeanMethods = false)
    @Import(ActuatorInfrastructureTestConfiguration.class)
    @ComponentScan(basePackages = {"com.kellen.config.web", "com.kellen.security.config"})
    static class ActuatorPackageExcludedApplication {
    }
}
