package com.kellen.config.actuator;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Actuator 专用安全链与业务服务自带的安全链并存时仍然生效。
 *
 * <p>仿照现有服务：user 用最高优先级链放行短信登录路径，ai 用最高优先级链放行 ASYNC 派发，
 * report、ai 还有若干按路径前缀匹配的中间顺序链。这些链与 Actuator 链同时存在时，上下文必须能启动，
 * 未带令牌的 Actuator 请求仍必须被拒绝。</p>
 */
@SpringBootTest(
        classes = {ActuatorProtectionTestApplication.class, ActuatorConsumerChainCoexistenceTest.ConsumerSecurityChains.class},
        properties = {
                "management.endpoints.web.exposure.include=*",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorConsumerChainCoexistenceTest extends ActuatorProtectionTestSupport {

    /**
     * 业务服务的最高优先级链和路径链都不能让未带令牌的 env 请求通过。
     */
    @Test
    void envEndpointStaysProtectedAlongsideConsumerChains() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 并存时携带正确令牌仍能访问。
     */
    @Test
    void envEndpointAllowsConfiguredTokenAlongsideConsumerChains() throws Exception {
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, TEST_TOKEN))
                .andExpect(status().isOk());
    }

    /**
     * 仿照业务服务自带的安全链。
     */
    @Configuration(proxyBeanMethods = false)
    static class ConsumerSecurityChains {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain smsPublicChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/auth/sms-codes", "/auth/sms-sessions");
            http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain asyncDispatchChain(HttpSecurity http) throws Exception {
            http.securityMatcher(request -> request.getDispatcherType() == DispatcherType.ASYNC);
            http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        @Order(5)
        SecurityFilterChain openApiPrefixChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/v1/**");
            http.authorizeHttpRequests(registry -> registry.anyRequest().denyAll());
            return http.build();
        }
    }
}
