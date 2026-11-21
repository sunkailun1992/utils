package com.kellen.security.oauth2;

import com.kellen.security.oauth2.test.FakeIssuer;
import jakarta.servlet.Filter;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ServiceBean;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 业务上下文的 Dubbo 引用启动时不初始化，第一次需要业务上下文的请求才初始化并调到提供方；之后的请求沿用同一个引用。
 * 提供方在本进程导出（injvm），不连注册中心，同 ai-agent 的 {@code DubboReferenceStartupTest}。
 *
 * <p>测试类结束即关闭上下文：Dubbo 的 injvm 导出在同一 JVM 的所有上下文之间共享，留在测试上下文缓存里会让别的测试类
 * 里的引用找到这个提供方（本进程有导出时引用默认走本进程调用），结论随执行顺序变化。</p>
 */
@DirtiesContext
@SpringBootTest(
        classes = FleetSecurityContextReferenceFirstCallTest.TestApplication.class,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jws-algorithms=RS256",
                "spring.security.oauth2.resourceserver.jwt.audiences=github-api",
                "fleet.security.oauth2.context-cache-ttl=0s",
                "dubbo.application.name=fleet-resource-server-test",
                "dubbo.application.qos-enable=false",
                "dubbo.registry.address=N/A",
                "dubbo.protocol.name=injvm"
        }
)
class FleetSecurityContextReferenceFirstCallTest {

    static final FakeIssuer KEYCLOAK = FakeIssuer.start();

    static final StubSecurityContextRpcService PROVIDER = new StubSecurityContextRpcService();

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
    void firstAuthenticatedRequestInitializesTheReferenceAndReachesTheProvider() throws Exception {
        PROVIDER.enabledUser("u1", "t1", "d1", List.of());
        ReferenceConfig<?> reference = FleetSecurityContextReferenceWithoutRegistryTest.securityContextReference(referenceBeanManager);
        assertThat(reference.configInitialized()).isFalse();
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.tenantId").value("t1"));
        assertThat(reference.configInitialized()).isTrue();
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + KEYCLOAK.userToken("u1")))
                .andExpect(status().isOk());
        assertThat(PROVIDER.calls()).containsExactly("u1|null", "u1|null");
    }

    /**
     * 测试应用：同 {@link FleetSecurityContextReferenceWithoutRegistryTest} 的接线，另在本进程导出业务上下文提供方
     * （只导出到 injvm，不作为 Spring 的 {@link SecurityContextRpcService} Bean，引用照常由自动配置创建）。
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

        @Bean
        ServiceBean<SecurityContextRpcService> securityContextProvider(ApplicationContext context) {
            ServiceBean<SecurityContextRpcService> service = new ServiceBean<>(context);
            service.setInterface(SecurityContextRpcService.class);
            service.setScope("local");
            service.setRef(PROVIDER);
            return service;
        }
    }
}
