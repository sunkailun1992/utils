package com.kellen.config.actuator;

import com.kellen.security.config.ActuatorSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证统一认证主安全链的公开白名单只保留 health，不再把整个 Actuator 当成公开接口。
 *
 * <p>这里显式排除 {@link ActuatorSecurityConfig}，只留主安全链，确认 Actuator 专用安全链缺席时
 * 开启认证的服务仍有兜底，不会因白名单整体放行 Actuator。</p>
 */
@SpringBootTest(
        classes = MainSecurityChainActuatorWhitelistTest.MainSecurityChainOnlyApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "security.auth.enabled=true"
        }
)
class MainSecurityChainActuatorWhitelistTest extends ActuatorProtectionTestSupport {

    /**
     * 没有 Actuator 专用安全链时，匿名请求也不能借公开白名单读取 env；
     * 主链返回 401 还是 403 取决于认证入口实现，两者都表示没有被白名单放行。
     */
    @Test
    void anonymousEnvRequestIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    /**
     * 主安全链兜底时 health 仍在公开白名单内。
     */
    @Test
    void healthEndpointStaysWhitelisted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * 只装配统一认证主安全链、不装配 Actuator 专用安全链的应用。
     */
    @Configuration(proxyBeanMethods = false)
    @Import(ActuatorInfrastructureTestConfiguration.class)
    @ComponentScan(
            basePackages = {"com.kellen.config.web", "com.kellen.security.config"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ActuatorSecurityConfig.class)
    )
    static class MainSecurityChainOnlyApplication {
    }
}
