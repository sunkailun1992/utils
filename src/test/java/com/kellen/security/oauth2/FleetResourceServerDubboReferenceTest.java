package com.kellen.security.oauth2;

import jakarta.servlet.Filter;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dubbo 已启用、本服务不提供业务上下文接口时，自动配置经 Dubbo 引用 user 服务；没有提供方时返回 503，不放行也不当作登录失效。
 * 不连注册中心：引用走本进程调用（scope=local），进程里没有导出该接口，调用即“没有提供方”。
 */
@SpringBootTest(
        classes = FleetResourceServerDubboReferenceTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.context-cache-ttl=0s",
                "dubbo.application.name=fleet-resource-server-test",
                "dubbo.application.qos-enable=false",
                "dubbo.registry.address=N/A",
                "dubbo.consumer.scope=local",
                "dubbo.consumer.check=false"
        }
)
class FleetResourceServerDubboReferenceTest {

    static final TestKeycloak KEYCLOAK = TestKeycloak.start();

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KEYCLOAK::issuer);
    }

    @AfterAll
    static void stopKeycloak() throws Exception {
        KEYCLOAK.close();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Test
    void dubboReferenceIsCreatedAndMissingProviderIs503() throws Exception {
        Map<String, SecurityContextRpcService> services = context.getBeansOfType(SecurityContextRpcService.class);
        assertThat(services).hasSize(1);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * 测试应用：在资源服务器组合上加 Dubbo 自动配置，不登记本地的业务上下文实现。
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration({
            PropertyPlaceholderAutoConfiguration.class,
            ConfigurationPropertiesAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            DubboRelaxedBinding2AutoConfiguration.class,
            DubboAutoConfiguration.class,
            FleetResourceServerAutoConfiguration.class,
            FleetSecurityContextDubboAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
    })
    @Import(FleetResourceServerTest.ProbeController.class)
    static class TestApplication {
    }
}
