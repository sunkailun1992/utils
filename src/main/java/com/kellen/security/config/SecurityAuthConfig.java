package com.kellen.security.config;

import com.kellen.security.SecurityAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 自动配置。
 *
 * <p>认证默认关闭，业务服务显式设置 {@code security.auth.enabled=true} 后才会启用鉴权。</p>
 *
 * @author 孙凯伦
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityAuthProperties.class)
public class SecurityAuthConfig {

    /**
     * 认证配置属性。
     */
    private final SecurityAuthProperties securityAuthProperties;

    /**
     * 构造 Spring Security 自动配置。
     *
     * @param securityAuthProperties 认证配置属性
     */
    public SecurityAuthConfig(SecurityAuthProperties securityAuthProperties) {
        this.securityAuthProperties = securityAuthProperties; // 保存认证配置，供过滤器和安全链使用。
    }

    /**
     * 创建认证上下文过滤器。
     *
     * @return 认证上下文过滤器
     */
    @Bean
    public SecurityAuthenticationFilter securityAuthenticationFilter() {
        return new SecurityAuthenticationFilter(securityAuthProperties); // 将认证配置传入过滤器。
    }

    /**
     * 创建 Spring Security 过滤器链。
     *
     * @param http Spring Security HTTP配置对象
     * @return Spring Security过滤器链
     * @throws Exception 安全链构造异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()); // 公共后端 API 使用无状态 JWT，不启用 CSRF。
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)); // 禁用服务端 Session，避免认证状态落到容器会话。
        http.addFilterBefore(securityAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class); // 在用户名密码过滤器前写入统一用户上下文。
        http.authorizeHttpRequests(registry -> {
            if (!securityAuthProperties.isEnabled()) {
                registry.anyRequest().permitAll(); // 认证总开关关闭时放行所有请求，兼容未接入认证的服务。
                return;
            }
            securityAuthProperties.getPermitUrls().forEach(url -> registry.requestMatchers(url).permitAll()); // 放行配置中的公开接口。
            registry.anyRequest().authenticated(); // 其余接口必须存在认证用户。
        });
        return http.build(); // 构建并返回安全过滤器链。
    }
}
