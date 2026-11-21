package com.kellen.security.oauth2;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;

/**
 * 标准 OAuth2 资源服务器（servlet）：配了 {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} 的服务自动接入。
 *
 * <p>注册顺序排在 Spring Boot 的资源服务器、OAuth2 客户端（oauth2Login 默认链）与默认安全链之前，这些默认链都以
 * 「没有自定义链」为条件，先注册本链它们就不再创建。</p>
 *
 * <ul>
 *     <li>令牌校验用 Spring Boot 按 issuer-uri 延迟发现的解码器：签发方、受众（{@code audiences}，没配时要求 {@value #DEFAULT_AUDIENCE}）、
 *     有效期（默认允许 60 秒时钟偏差）、签发时间（{@link FleetIssuedAtValidator}：必须有，不能晚于当前时间 60 秒以上），
 *     并让 {@code jws-algorithms}（缺省 RS256）在 issuer 方式下同样生效。</li>
 *     <li>默认过滤链：无状态、关 CSRF（只接 Bearer 令牌）、{@code fleet.security.oauth2.permit-paths} 放行，其余要求业务上下文可用的用户
 *     或名单里的机器客户端；401、403、503 返回统一 JSON。顺序与 Spring Boot 默认链相同（{@link SecurityFilterProperties#BASIC_AUTH_ORDER}），
 *     排在 Actuator 链和服务自己的路径专用链之后。</li>
 *     <li>业务上下文经 {@link SecurityUserCache} 取自 {@link SecurityContextRpcService}：本服务提供时用本地实现（提供方标 {@code @Primary}），
 *     否则用 {@link FleetSecurityContextDubboAutoConfiguration} 建的 Dubbo 引用；转换器在请求时才取提供方。两者都没有时启动失败
 *     （{@link FleetSecurityContextProviderFailureAnalyzer} 给出原因与做法）。</li>
 * </ul>
 *
 * @author 孙凯伦
 */
@AutoConfiguration(before = {OAuth2ResourceServerAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class, SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(BearerTokenAuthenticationToken.class)
@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
@EnableConfigurationProperties({FleetOAuth2Properties.class, OAuth2ResourceServerProperties.class})
@EnableWebSecurity
@EnableMethodSecurity
public class FleetResourceServerAutoConfiguration {

    /**
     * 服务没有配置 {@code spring.security.oauth2.resourceserver.jwt.audiences} 时要求的受众：网关 BFF 转给业务后端的用户令牌受众。
     */
    public static final String DEFAULT_AUDIENCE = "github-api";

    /**
     * 由 Spring Boot 按自动配置加载。
     */
    public FleetResourceServerAutoConfiguration() {
    }

    /**
     * 受众缺省值：服务没配 {@code spring.security.oauth2.resourceserver.jwt.audiences} 时只收受众含 {@value #DEFAULT_AUDIENCE} 的令牌，
     * 漏配不会变成不校验受众（否则会接受本域发给别的服务的令牌）；配了就交给 Spring Boot 的受众校验，这里放行。
     * Spring Boot 按 issuer-uri 建解码器时会装上容器里的 {@link OAuth2TokenValidator} Bean。
     *
     * @param resourceServer Spring Boot 的资源服务器配置
     * @return 令牌校验器
     */
    @Bean
    public OAuth2TokenValidator<Jwt> fleetDefaultAudienceValidator(OAuth2ResourceServerProperties resourceServer) {
        JwtClaimValidator<List<String>> defaultAudience = new JwtClaimValidator<>(JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(DEFAULT_AUDIENCE));
        return jwt -> resourceServer.getJwt().getAudiences().isEmpty() ? defaultAudience.validate(jwt) : OAuth2TokenValidatorResult.success();
    }

    /**
     * 签发时间校验：iat 必须有，且不能晚于当前时间 60 秒以上（Keycloak 不写 nbf，只看 exp 时未来签发的令牌能一直用到 iat 加有效期）。
     * Spring Boot 按 issuer-uri 建解码器时会装上容器里的 {@link OAuth2TokenValidator} Bean。
     *
     * @return 令牌校验器
     */
    @Bean
    public OAuth2TokenValidator<Jwt> fleetIssuedAtValidator() {
        return new FleetIssuedAtValidator();
    }

    /**
     * 启动检查：资源服务器离不开业务上下文提供方，本服务既没有实现 {@link SecurityContextRpcService}、也没有 Dubbo 引用时启动失败，
     * 不再照常启动后把每个用户令牌都当成用户不存在。只按 Bean 定义查类型，不提前初始化 Dubbo 引用（引用 {@code init = false}）；
     * 连父上下文一起查，与转换器在请求时经 {@code ObjectProvider} 查找的口径一致。
     *
     * @param beanFactory Bean 工厂
     * @return 在全部单例创建之后执行的检查
     */
    @Bean
    public SmartInitializingSingleton fleetSecurityContextProviderCheck(ListableBeanFactory beanFactory) {
        return () -> {
            if (BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, SecurityContextRpcService.class, true, false).length == 0) {
                throw new FleetSecurityContextProviderMissingException("资源服务器（已配置 spring.security.oauth2.resourceserver.jwt.issuer-uri）"
                        + "没有业务上下文提供方 SecurityContextRpcService：本服务既没有实现它，也没有启用 Dubbo 取得 user 服务的引用。");
            }
        };
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
     * @param resourceServer Spring Boot 的资源服务器配置（签发方即 Keycloak 域地址）
     * @return 过滤链
     */
    @Bean
    @Order(SecurityFilterProperties.BASIC_AUTH_ORDER)
    public SecurityFilterChain fleetApiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                           FleetJwtAuthenticationConverter converter, FleetOAuth2Properties properties,
                                                           OAuth2ResourceServerProperties resourceServer) {
        FleetSecurityErrorHandler errors = new FleetSecurityErrorHandler();
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(registry -> {
            properties.getPermitPaths().forEach(path -> registry.requestMatchers(PathPatternRequestMatcher.pathPattern(path)).permitAll());
            registry.anyRequest().access(new FleetAuthorizationManager(properties.getRequiredAzp()));
        });
        String issuer = resourceServer.getJwt().getIssuerUri();
        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(new FailClosedJwtDecoder(jwtDecoder)).jwtAuthenticationConverter(converter))
                // RFC 9728：Spring Security 7 在每条资源服务器链上发布 /.well-known/oauth-protected-resource（资源标识按请求推出），
                // 这里声明授权服务器就是 Keycloak 签发方，客户端据此发现去哪里取令牌。
                .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                        builder -> builder.authorizationServer(issuer)))
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
}
