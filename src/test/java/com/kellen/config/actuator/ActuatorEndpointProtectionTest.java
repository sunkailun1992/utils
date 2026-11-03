package com.kellen.config.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证内网直连实例端口时 Actuator 端点必须携带令牌，只有健康检查保持公开。
 *
 * <p>配置复现正式环境：端点全部暴露、统一认证关闭（多数服务的默认值）、业务拦截器照常注册。</p>
 */
@SpringBootTest(
        classes = ActuatorProtectionTestApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "management.endpoint.health.probes.enabled=true",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorEndpointProtectionTest extends ActuatorProtectionTestSupport {

    @Autowired
    private ActuatorProtectionTestApplication.ProbeWriteEndpoint probeWriteEndpoint;

    /**
     * env 会回显配置来源和部分取值，未带令牌必须在进入端点前被拒绝。
     */
    @Test
    void envEndpointRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 端点目录会列出 shutdown、env、nacosconfig 等入口，同样不能匿名读取。
     */
    @Test
    void endpointLinksRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 令牌值不匹配时不能因为请求头存在就放行。
     */
    @Test
    void envEndpointRejectsWrongToken() throws Exception {
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, TEST_TOKEN + "-wrong"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 写端点未带令牌时必须拒绝，并且端点方法不能被执行。
     */
    @Test
    void writeOperationWithoutTokenIsRejectedBeforeInvocation() throws Exception {
        int invocationsBefore = probeWriteEndpoint.invocations();

        mockMvc.perform(post("/actuator/probewrite"))
                .andExpect(status().isUnauthorized());

        assertThat(probeWriteEndpoint.invocations()).isEqualTo(invocationsBefore);
    }

    /**
     * 携带正确令牌的运维请求仍能读取 env，避免保护把运维入口一起封死。
     */
    @Test
    void envEndpointAllowsConfiguredToken() throws Exception {
        mockMvc.perform(get("/actuator/env").header(TOKEN_HEADER, TEST_TOKEN))
                .andExpect(status().isOk());
    }

    /**
     * 携带正确令牌的 POST 写请求必须能执行，Actuator 安全链不能因为 CSRF 拦截写端点。
     */
    @Test
    void writeOperationAllowsConfiguredToken() throws Exception {
        int invocationsBefore = probeWriteEndpoint.invocations();

        mockMvc.perform(post("/actuator/probewrite").header(TOKEN_HEADER, TEST_TOKEN))
                .andExpect(status().isOk());

        assertThat(probeWriteEndpoint.invocations()).isEqualTo(invocationsBefore + 1);
    }

    /**
     * 部署脚本和容器探活不带令牌访问 health，必须保持可用。
     */
    @Test
    void healthEndpointStaysPublicForProbes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Kubernetes 存活/就绪探针使用 health 子路径，同样不需要令牌。
     */
    @Test
    void healthProbeGroupStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
