package com.kellen.config.web;

import com.kellen.config.actuator.ActuatorInterceptor;
import com.kellen.security.config.TenantProperties;
import com.kellen.traffic.TrafficGovernanceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 请求拦截器配置。
 *
 * @author 孙凯伦
 */
@Configuration
@EnableConfigurationProperties({TenantProperties.class, TrafficGovernanceProperties.class})
public class ReqConfig implements WebMvcConfigurer {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 流量治理配置属性。
     */
    private final TrafficGovernanceProperties trafficGovernanceProperties;

    /**
     * 构造 Web MVC 请求拦截器配置。
     *
     * @param tenantProperties             租户配置属性
     * @param trafficGovernanceProperties 流量治理配置属性
     */
    public ReqConfig(TenantProperties tenantProperties, TrafficGovernanceProperties trafficGovernanceProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供请求拦截器使用。
        this.trafficGovernanceProperties = trafficGovernanceProperties; // 保存流量治理配置，供请求拦截器使用。
    }

    /**
     * 注册 Web MVC 请求拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ReqInterceptor(tenantProperties, trafficGovernanceProperties)).addPathPatterns("/**"); // 所有接口都经过租户和流量治理上下文初始化与清理。
        registry.addInterceptor(new ActuatorInterceptor()).addPathPatterns("/actuator/**", "/actuator"); // Actuator 访问继续校验内部请求头，避免依赖 Boot 内部 HandlerMapping。
    }
}
