package com.kellen.config.web;

import com.kellen.security.config.TenantProperties;
import com.kellen.security.config.SecurityAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import com.kellen.traffic.TrafficGovernanceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 请求拦截器配置。
 *
 * <p>这里注册的拦截器只作用于业务 Controller 的 HandlerMapping，Actuator 端点由 Boot 独立的
 * HandlerMapping 处理、不会经过这些拦截器；Actuator 访问保护见 {@code ActuatorSecurityConfig}。</p>
 *
 * @author 孙凯伦
 */
@Configuration
@EnableConfigurationProperties({TenantProperties.class, TrafficGovernanceProperties.class, SecurityAuthProperties.class})
public class ReqConfig implements WebMvcConfigurer {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 流量治理配置属性。
     */
    private final TrafficGovernanceProperties trafficGovernanceProperties;

    private final SecurityAuthProperties securityAuthProperties;

    /**
     * 构造 Web MVC 请求拦截器配置。
     *
     * @param tenantProperties             租户配置属性
     * @param trafficGovernanceProperties 流量治理配置属性
     */
    public ReqConfig(TenantProperties tenantProperties, TrafficGovernanceProperties trafficGovernanceProperties) {
        this(tenantProperties, trafficGovernanceProperties, new SecurityAuthProperties());
    }

    @Autowired
    public ReqConfig(TenantProperties tenantProperties, TrafficGovernanceProperties trafficGovernanceProperties,
                     SecurityAuthProperties securityAuthProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供请求拦截器使用。
        this.trafficGovernanceProperties = trafficGovernanceProperties; // 保存流量治理配置，供请求拦截器使用。
        this.securityAuthProperties = securityAuthProperties;
    }

    /**
     * 注册 Web MVC 请求拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ReqInterceptor(tenantProperties, trafficGovernanceProperties, securityAuthProperties)).addPathPatterns("/**");
    }
}
