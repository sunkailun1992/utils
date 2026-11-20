package com.kellen.config.actuator;

import com.kellen.security.config.ActuatorSecurityConfig;
import com.kellen.security.oauth2.FleetResourceServerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只有资源服务器默认链（排除 Actuator 专用链）时，它的放行名单只放 health，其余 Actuator 端点照样要求令牌。
 */
@SpringBootTest(
        classes = MainSecurityChainActuatorWhitelistTest.MainSecurityChainOnlyApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/realms/github-dev"
        }
)
class MainSecurityChainActuatorWhitelistTest extends ActuatorProtectionTestSupport {

    @Test
    void anonymousEnvRequestIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointStaysWhitelisted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ActuatorInfrastructureTestConfiguration.class)
    @ImportAutoConfiguration({FleetResourceServerAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
    @ComponentScan(
            basePackages = {"com.kellen.config.web", "com.kellen.security.config"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ActuatorSecurityConfig.class)
    )
    static class MainSecurityChainOnlyApplication {
    }
}
