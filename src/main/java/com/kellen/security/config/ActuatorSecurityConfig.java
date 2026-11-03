package com.kellen.security.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Actuator 端点访问保护配置。
 *
 * <p>Actuator 端点由 Spring Boot 独立的 {@code WebMvcEndpointHandlerMapping} 处理，
 * {@code WebMvcConfigurer#addInterceptors} 注册的拦截器不会作用到端点上，因此改为在 Spring Security 中
 * 为全部已暴露端点建一条优先匹配的过滤链：health（含探针子路径）保持公开，其余端点（shutdown、shutdownGraceFul、
 * env、refresh、nacosconfig、nacosdiscovery、serviceregistry 等）必须在 {@code actuatorReq} 请求头携带
 * {@code security.actuator.token}，否则返回 401。</p>
 *
 * <p>令牌只从 Nacos、环境变量等运行时配置读取，公共包没有默认值；未配置时除 health 外一律拒绝。
 * 每次请求都从 {@link Environment} 读取当前值：Nacos 刷新新增、轮换或删除令牌都无需重启即可生效，
 * 删除配置即可立即撤销旧令牌。</p>
 *
 * <p>这条链与 {@code security.auth.enabled} 无关，未开启统一认证的服务同样受保护；配置独立管理端口时，
 * Spring Boot 会把同一个 {@code springSecurityFilterChain} 挂到管理端口上。消费者若确需放开其它端点，
 * 应在服务内显式声明顺序值小于 {@link #FILTER_CHAIN_ORDER} 的过滤链并说明原因，公共包不提供关闭开关。</p>
 *
 * @author 孙凯伦
 */
@Configuration
public class ActuatorSecurityConfig {

    /**
     * Actuator 专用过滤链的顺序。
     *
     * <p>排在统一认证主链（无 {@code @Order}）和业务服务的路径专用链之前；在 {@link Ordered#HIGHEST_PRECEDENCE}
     * 之后留出位置，顺序固定，不会和服务里已有的最高优先级链（短信登录路径、ASYNC 派发）因注册顺序互换。</p>
     */
    public static final int FILTER_CHAIN_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * 日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(ActuatorSecurityConfig.class);

    /**
     * 部署脚本和容器探活使用的端点 ID。
     */
    private static final String HEALTH_ENDPOINT_ID = "health";

    /**
     * 运维令牌配置键，支持 {@code SECURITY_ACTUATOR_TOKEN} 等宽松绑定写法。
     */
    private static final String TOKEN_PROPERTY = "security.actuator.token";

    /**
     * 当前运行环境，Nacos 刷新后会替换其中的属性源。
     */
    private final Environment environment;

    /**
     * 构造 Actuator 端点访问保护配置。
     *
     * @param environment 当前运行环境
     */
    public ActuatorSecurityConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 创建 Actuator 专用过滤链。
     *
     * <p>必须排在统一认证主链之前：主链在认证关闭时放行全部请求，开启认证时也只校验登录用户，
     * 都不能替代运维令牌。</p>
     *
     * @param http Spring Security HTTP 配置对象
     * @return Actuator 专用过滤链
     * @throws Exception 安全链构造异常
     */
    @Bean
    @Order(FILTER_CHAIN_ORDER)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        if (!StringUtils.hasText(environment.getProperty(TOKEN_PROPERTY))) {
            log.warn("security.actuator.token 未配置，除 health 外的 Actuator 端点将一律返回 401");
        }
        http.securityMatcher(EndpointRequest.toAnyEndpoint());
        // 运维调用方不持有浏览器会话，令牌放在请求头里；写端点（POST）若保留 CSRF 会被一律拦截。
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(registry -> registry
                .requestMatchers(EndpointRequest.to(HEALTH_ENDPOINT_ID)).permitAll()
                .anyRequest().access(new ActuatorTokenAuthorizationManager(() -> environment.getProperty(TOKEN_PROPERTY))));
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(this::rejectWithoutToken));
        return http.build();
    }

    /**
     * 拒绝未携带有效令牌的 Actuator 请求。
     *
     * <p>只记录方法和路径，不记录请求头，避免把调用方误传的令牌写进日志。</p>
     *
     * @param request   当前HTTP请求
     * @param response  当前HTTP响应
     * @param exception 触发拒绝的认证异常
     */
    private void rejectWithoutToken(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        log.warn("拒绝未携带有效令牌的 Actuator 请求：{} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
