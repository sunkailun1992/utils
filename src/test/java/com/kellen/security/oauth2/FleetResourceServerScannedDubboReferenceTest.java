package com.kellen.security.oauth2;

import com.kellen.security.oauth2.test.FakeIssuer;
import jakarta.servlet.Filter;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
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
 * 按消费服务的真实接线复核业务上下文的 Dubbo 引用：主类带 {@code @EnableDubbo}，{@code @SpringBootApplication} 默认扫描
 * 整个 {@code com.kellen}（包括 utils 的类），扫描过滤器与 {@code @SpringBootApplication} 相同。引用必须建出来，
 * 没有提供方时返回 503；建不出来时令牌用户会全部按不存在处理（401），与 2026-11-21 九个服务重启后的现象一致。
 * 不连注册中心：引用走本进程调用（scope=local），进程里没有导出该接口，调用即“没有提供方”。
 */
@SpringBootTest(
        classes = FleetResourceServerScannedDubboReferenceTest.TestApplication.class,
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
class FleetResourceServerScannedDubboReferenceTest {

    static final FakeIssuer KEYCLOAK = FakeIssuer.start();

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
     * 测试应用：与消费服务主类同样带 {@code @EnableDubbo}，并按 {@code @SpringBootApplication} 的过滤器扫描 utils 的 oauth2 包；
     * 自动配置组合与 {@link FleetResourceServerDubboReferenceTest} 相同，不登记本地的业务上下文实现。
     */
    @SpringBootConfiguration
    @EnableDubbo
    @ComponentScan(basePackageClasses = FleetResourceServerAutoConfiguration.class, excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)})
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
