package com.kellen.security.oauth2;

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 标准 OAuth2 资源服务器（servlet）：配了 {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} 的服务自动接入。
 *
 * <p>注册顺序排在 Spring Boot 的资源服务器、OAuth2 客户端（oauth2Login 默认链）与默认安全链之前，这些默认链都以
 * 「没有自定义链」为条件，先注册本链它们就不再创建。</p>
 *
 * <ul>
 *     <li>令牌校验用 Spring Boot 按 issuer-uri 延迟发现的解码器：签发方、受众（{@code audiences}）、有效期（默认允许 60 秒时钟偏差），
 *     并让 {@code jws-algorithms}（缺省 RS256）在 issuer 方式下同样生效。</li>
 *     <li>默认过滤链：无状态、关 CSRF（只接 Bearer 令牌）、{@code fleet.security.oauth2.permit-paths} 放行，其余要求业务上下文可用的用户
 *     或名单里的机器客户端；401、403、503 返回统一 JSON。顺序与 Spring Boot 默认链相同（{@link SecurityFilterProperties#BASIC_AUTH_ORDER}），
 *     排在 Actuator 链和服务自己的路径专用链之后。</li>
 *     <li>业务上下文经 {@link SecurityUserCache} 取自 {@link SecurityContextRpcService}：本服务提供时用本地实现（提供方标 {@code @Primary}），
 *     否则用 Dubbo 引用。</li>
 * </ul>
 *
 * @author 孙凯伦
 */
@AutoConfiguration(before = {OAuth2ResourceServerAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class, SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class}, after = DubboAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(BearerTokenAuthenticationToken.class)
@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
@EnableConfigurationProperties({FleetOAuth2Properties.class, OAuth2ResourceServerProperties.class})
@EnableWebSecurity
@EnableMethodSecurity
public class FleetResourceServerAutoConfiguration {

    /**
     * 由 Spring Boot 按自动配置加载。
     */
    public FleetResourceServerAutoConfiguration() {
    }

    /**
     * 业务上下文缓存；没有 Redis 时只用本地缓存。
     *
     * @param redis      Redis 客户端
     * @param properties utils 资源服务器配置
     * @return 缓存
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityUserCache securityUserCache(ObjectProvider<StringRedisTemplate> redis, FleetOAuth2Properties properties) {
        return new SecurityUserCache(redis.getIfAvailable(), properties.getContextCacheTtl());
    }

    /**
     * 让 {@code spring.security.oauth2.resourceserver.jwt.jws-algorithms} 在 issuer-uri 方式下也生效：
     * Spring Boot 只在 jwk-set-uri 方式应用该属性，issuer 方式会按公钥集推断算法。
     *
     * @param properties Spring Boot 资源服务器配置
     * @return 解码器构建定制
     */
    @Bean
    public JwkSetUriJwtDecoderBuilderCustomizer fleetJwsAlgorithmsCustomizer(OAuth2ResourceServerProperties properties) {
        return builder -> builder.jwsAlgorithms(algorithms -> properties.getJwt().getJwsAlgorithms().forEach(name -> {
            SignatureAlgorithm algorithm = SignatureAlgorithm.from(name);
            if (algorithm == null) {
                throw new IllegalStateException("spring.security.oauth2.resourceserver.jwt.jws-algorithms 含未知算法：" + name);
            }
            algorithms.add(algorithm);
        }));
    }

    /**
     * 访问令牌到认证结果的转换器。
     *
     * @param properties     utils 资源服务器配置
     * @param cache          业务上下文缓存
     * @param contextService 业务上下文提供方
     * @return 转换器
     */
    @Bean
    @ConditionalOnMissingBean
    public FleetJwtAuthenticationConverter fleetJwtAuthenticationConverter(FleetOAuth2Properties properties, SecurityUserCache cache,
                                                                           ObjectProvider<SecurityContextRpcService> contextService) {
        return new FleetJwtAuthenticationConverter(properties, cache, contextService);
    }

    /**
     * 业务接口的默认过滤链。
     *
     * @param http       Spring Security HTTP 配置
     * @param jwtDecoder Spring Boot 按 issuer-uri 创建的解码器
     * @param converter  令牌转换器
     * @param properties utils 资源服务器配置
     * @return 过滤链
     */
    @Bean
    @Order(SecurityFilterProperties.BASIC_AUTH_ORDER)
    public SecurityFilterChain fleetApiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                           FleetJwtAuthenticationConverter converter, FleetOAuth2Properties properties) {
        FleetSecurityErrorHandler errors = new FleetSecurityErrorHandler();
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(registry -> {
            properties.getPermitPaths().forEach(path -> registry.requestMatchers(PathPatternRequestMatcher.pathPattern(path)).permitAll());
            registry.anyRequest().access(new FleetAuthorizationManager(properties.getRequiredAzp()));
        });
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.decoder(new FailClosedJwtDecoder(jwtDecoder)).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors)
                .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                    @Override
                    public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                        // 认证服务异常（签发方不可达、user 服务不可用）默认会被重新抛出成 500，这里改为交给统一响应。
                        AuthenticationEntryPointFailureHandler failureHandler = new AuthenticationEntryPointFailureHandler(errors);
                        failureHandler.setRethrowAuthenticationServiceException(false);
                        filter.setAuthenticationFailureHandler(failureHandler);
                        return filter;
                    }
                }));
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        http.addFilterAfter(new FleetSecurityContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 本服务不提供 {@link SecurityContextRpcService} 时，经 Dubbo 引用 user 服务的实现。只在 Dubbo 已启用
     * （{@code @EnableDubbo} 或 Dubbo 自动配置注册了引用注解处理器）时创建；没有引用也没有本地实现时，令牌用户按不存在处理（401）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ReferenceBean.class)
    @ConditionalOnBean(ReferenceAnnotationBeanPostProcessor.class)
    static class DubboSecurityContextConfiguration {

        /**
         * user 服务的业务上下文接口；参数沿用各服务既有引用的写法（不在启动时检查、首次调用才连接、不重试、3 秒超时、跟随灰度路由）。
         *
         * @return Dubbo 引用
         */
        @Bean
        @ConditionalOnMissingBean(SecurityContextRpcService.class)
        @DubboReference(check = false, lazy = true, timeout = 3000, retries = 0, parameters = {"traffic.canary.enabled", "true"})
        public ReferenceBean<SecurityContextRpcService> securityContextRpcService() {
            return new ReferenceBean<>();
        }
    }
}
