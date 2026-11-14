package com.kellen.config.actuator;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Actuator 访问保护测试的公共 MockMvc 装配。
 *
 * <p>生产环境由 Servlet 容器把 {@code springSecurityFilterChain} 挂到所有请求前；这里手工挂同一个过滤器，
 * 让请求按真实顺序经过 Spring Security 再进入 Actuator 的 HandlerMapping。</p>
 */
abstract class ActuatorProtectionTestSupport {

    /**
     * 运维调用方携带 Actuator 令牌的请求头。
     */
    static final String TOKEN_HEADER = "actuatorReq";

    /**
     * 测试配置的 Actuator 令牌，仅用于本测试上下文。
     */
    static final String TEST_TOKEN = "unit-test-actuator-token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }
}
