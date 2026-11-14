package com.kellen.config.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证会让实例下线的 Spring Boot shutdown 端点不能匿名调用。
 *
 * <p>保护失效时端点会真的关闭测试上下文，所以每个用例结束后都重建上下文，避免影响其它用例。</p>
 */
@SpringBootTest(
        classes = ActuatorProtectionTestApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "management.endpoint.shutdown.access=unrestricted",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ActuatorShutdownEndpointProtectionTest extends ActuatorProtectionTestSupport {

    /**
     * 匿名调用 Spring Boot 自带 shutdown 端点必须被拒绝。
     */
    @Test
    void bootShutdownWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/actuator/shutdown"))
                .andExpect(status().isUnauthorized());
    }
}
