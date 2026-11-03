package com.kellen.config.actuator;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 Tomcat 验证路径变形不能绕过 Actuator 令牌校验。
 *
 * <p>直接写原始 HTTP 请求行，避免客户端预先规范化路径；内网调用方可以发出任意请求行，
 * 编码、尾斜线、分号参数、{@code ..} 回退等写法都必须被拒绝或无法命中端点。</p>
 */
@SpringBootTest(
        classes = {ActuatorProtectionTestApplication.class, ActuatorPathVariantProtectionTest.EmbeddedServerConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=*",
                "security.actuator.token=" + ActuatorProtectionTestSupport.TEST_TOKEN
        }
)
class ActuatorPathVariantProtectionTest {

    @MockitoBean
    NacosAutoServiceRegistration serviceRegistration;

    @LocalServerPort
    private int port;

    /**
     * 这些写法会被端点处理，未带令牌时必须返回 401。
     *
     * @param method 请求方法
     * @param path   原始请求路径
     */
    @ParameterizedTest
    @CsvSource({
            "GET, /actuator",
            "GET, /actuator/",
            "GET, /actuator/env/",
            "GET, /actuator/%65nv",
            "GET, /%61ctuator/env",
            "GET, /actuator/env?format=json",
            "HEAD, /actuator/env",
            "OPTIONS, /actuator/env",
            "POST, /actuator/probewrite"
    })
    void endpointPathVariantsRequireToken(String method, String path) throws Exception {
        assertThat(statusCode(method, path)).isEqualTo(401);
    }

    /**
     * 借 health 公开路径回退、双斜线、分号或编码斜线绕路时，不能拿到 2xx。
     *
     * @param path 原始请求路径
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health/../env",
            "/actuator/health/..;/env",
            "/actuator/health/%2e%2e/env",
            "/actuator/health;/../env",
            "//actuator/env",
            "/actuator//env",
            "/actuator;a=b/env",
            "/actuator%2fenv",
            "/ACTUATOR/env",
            "/actuator/env.json"
    })
    void traversalAndEncodingVariantsDoNotReachEndpoints(String path) throws Exception {
        int status = statusCode("GET", path);
        assertThat(status / 100).as("GET %s 返回 %s", path, status).isNotEqualTo(2);
    }

    private int statusCode(String method, String path) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            String request = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            String statusLine = in.readLine();
            assertThat(statusLine).as("%s %s 没有响应", method, path).isNotNull();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    /**
     * 补充真实 Tomcat 和 Security 过滤器注册，和消费者启动时的完整自动配置保持一致。
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({TomcatServletWebServerAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
    static class EmbeddedServerConfiguration {
    }
}
