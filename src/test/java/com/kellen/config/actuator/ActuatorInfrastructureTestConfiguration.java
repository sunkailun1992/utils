package com.kellen.config.actuator;

import org.springframework.boot.actuate.autoconfigure.context.ShutdownEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.jackson.JacksonEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.env.EnvironmentEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.actuate.web.ServletManagementContextAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.actuate.endpoint.web.WebMvcHealthEndpointExtensionAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 访问保护测试共用的自动配置集合。
 *
 * <p>utils 以 api 方式带入 Nacos、Seata、Redis、数据源等大量 starter，全量自动配置会在测试里尝试连接外部组件；
 * 这里只导入 MVC、Security 依赖的基础设施和 Actuator 端点，保证测试只验证请求在安全链和端点之间的走向。</p>
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({
        PropertyPlaceholderAutoConfiguration.class,
        ConfigurationPropertiesAutoConfiguration.class,
        ApplicationAvailabilityAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        EndpointAutoConfiguration.class,
        JacksonEndpointAutoConfiguration.class,
        WebEndpointAutoConfiguration.class,
        ManagementContextAutoConfiguration.class,
        ServletManagementContextAutoConfiguration.class,
        EnvironmentEndpointAutoConfiguration.class,
        ShutdownEndpointAutoConfiguration.class,
        HealthContributorAutoConfiguration.class,
        HealthContributorRegistryAutoConfiguration.class,
        HealthEndpointAutoConfiguration.class,
        AvailabilityProbesAutoConfiguration.class,
        WebMvcHealthEndpointExtensionAutoConfiguration.class
})
class ActuatorInfrastructureTestConfiguration {
}
