package com.kellen.security.config;

import com.kellen.security.SecurityAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.ArrayList;
import java.util.List;

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
     * 日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SecurityAuthConfig.class);

    /**
     * 框架内置公开接口。
     */
    private static final List<String> BUILTIN_PERMIT_URLS = List.of(
            "/actuator/**",
            "/webjars/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/.well-known/**",
            "/auth/tenants",
            "/auth/sessions",
            "/auth/sessions/refresh",
            "/auth/third-party/sessions",
            "/auth/open/signatures/verify",
            "/oauth2/authorize",
            "/oauth2/token"
    );

    /**
     * 认证配置属性。
     */
    private final SecurityAuthProperties securityAuthProperties;

    /**
     * Redis字符串客户端。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造 Spring Security 自动配置。
     *
     * @param securityAuthProperties 认证配置属性
     */
    public SecurityAuthConfig(SecurityAuthProperties securityAuthProperties, ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.securityAuthProperties = securityAuthProperties; // 保存认证配置，供过滤器和安全链使用。
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable(); // Redis 不存在时只解析 JWT，不启用服务端撤销状态。
    }

    /**
     * 创建认证上下文过滤器。
     *
     * @return 认证上下文过滤器
     */
    @Bean
    public SecurityAuthenticationFilter securityAuthenticationFilter() {
        return new SecurityAuthenticationFilter(securityAuthProperties, stringRedisTemplate); // 将认证配置和 token 生命周期 Redis 状态传入过滤器。
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
            List<String> permitUrls = resolvePermitUrls(); // 合并框架内置公开接口和外部配置公开接口，避免配置覆盖导致登录被拦截。
            log.info("Security auth enabled, permit urls: {}", permitUrls); // 启动时输出白名单，方便定位 Nacos 或本地配置是否真正生效。
            permitUrls.forEach(url -> registry.requestMatchers(PathPatternRequestMatcher.pathPattern(url)).permitAll()); // 使用 Security 7 PathPattern matcher 匹配 Nacos 白名单路径。
            registry.anyRequest().authenticated(); // 其余接口必须存在认证用户。
        });
        return http.build(); // 构建并返回安全过滤器链。
    }

    /**
     * 解析最终公开接口。
     *
     * @return 去重后的公开接口集合
     */
    private List<String> resolvePermitUrls() {
        List<String> permitUrls = new ArrayList<>(BUILTIN_PERMIT_URLS); // 先放入框架内置公开接口，保证登录、租户选择和文档接口稳定放行。
        securityAuthProperties.getPermitUrls().forEach(url -> {
            if (!permitUrls.contains(url)) {
                permitUrls.add(url); // 外部配置中的额外公开接口按顺序追加，保留服务自定义放行能力。
            }
        });
        return permitUrls; // 返回最终用于 Spring Security 匹配的白名单。
    }
}
