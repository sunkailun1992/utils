package com.kellen.security.oauth2;

import jakarta.servlet.Filter;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.dubbo.config.spring.reference.ReferenceBeanManager;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 业务上下文的 Dubbo 引用与各服务既有引用一样在启动时不初始化（{@code init = false}）：消费服务的完整上下文测试与工具常常不配
 * 注册中心（如 ai、ai-agent 的测试启动类，没有任何 {@code dubbo.registry.*}），启动时就初始化会让上下文直接起不来（Dubbo：
 * No such any registry to reference）。第一次需要业务上下文的请求才初始化引用；没有注册中心时初始化失败，按 user 服务
 * 不可用返回 503，不放行。注意不能用 {@code dubbo.registry.address=N/A} 代替“没配”：显式声明无注册中心时 Dubbo 3.3
 * 的引用可以初始化成功，复现不了这类上下文。
 */
@SpringBootTest(
        classes = FleetSecurityContextReferenceWithoutRegistryTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.context-cache-ttl=0s",
                "dubbo.application.name=fleet-resource-server-test",
                "dubbo.application.qos-enable=false"
        }
)
class FleetSecurityContextReferenceWithoutRegistryTest {

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

    @Autowired
    private ReferenceBeanManager referenceBeanManager;

    @Test
    void contextStartsWithTheReferenceUninitializedAndTheFirstRequestFailsClosed() throws Exception {
        ReferenceConfig<?> reference = securityContextReference(referenceBeanManager);
        assertThat(reference.configInitialized()).isFalse();
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isServiceUnavailable());
        assertThat(reference.configInitialized()).isFalse();
    }

    /**
     * 上下文里唯一的业务上下文 Dubbo 引用。
     *
     * @param manager Dubbo 的引用登记表
     * @return 引用配置
     */
    static ReferenceConfig<?> securityContextReference(ReferenceBeanManager manager) {
        List<ReferenceConfig<?>> references = manager.getReferences().stream()
                .filter(bean -> SecurityContextRpcService.class.equals(bean.getObjectType()))
                .<ReferenceConfig<?>>map(ReferenceBean::getReferenceConfig)
                .toList();
        assertThat(references).hasSize(1);
        return references.getFirst();
    }

    /**
     * 测试应用：与消费服务主类同样带 {@code @EnableDubbo}，按 {@code @SpringBootApplication} 的过滤器扫描 utils 的 oauth2 包；
     * 不配任何注册中心，也不把引用限定为本进程调用，同 ai、ai-agent 的测试启动类。
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
