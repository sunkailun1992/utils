package com.kellen.bean;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 请求拦截器配置。
 *
 * @author 孙凯伦
 */
@Configuration
public class ReqConfig implements WebMvcConfigurer {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 构造 Web MVC 请求拦截器配置。
     *
     * @param tenantProperties 租户配置属性
     */
    public ReqConfig(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供请求拦截器使用。
    }

    /**
     * 注册 Web MVC 请求拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ReqInterceptor(tenantProperties)).addPathPatterns("/**"); // 所有接口都经过租户上下文初始化和清理。
    }
}
