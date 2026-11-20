package com.kellen.security.oauth2.client;

import com.kellen.security.oauth2.FleetAuthenticationToken;
import com.kellen.security.oauth2.FleetOAuth2Properties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * 出站 OAuth2 客户端：配了 {@code spring.security.oauth2.client.registration.*} 的服务自动接入。
 *
 * <p>已授权客户端管理器用 {@link AuthorizedClientServiceOAuth2AuthorizedClientManager}（不依赖当前 HTTP 请求，后台线程同样可用），
 * 只开客户端凭证与令牌交换两种授权。令牌交换按 Keycloak 标准令牌交换的要求：subject token 以访问令牌类型
 * （{@code urn:ietf:params:oauth:token-type:access_token}）提交，并带上 {@code fleet.security.oauth2.token-exchange.<注册ID>.audience}。</p>
 *
 * <p>令牌端点请配 {@code spring.security.oauth2.client.provider.<名称>.token-uri}，不配 issuer-uri：
 * Spring Boot 对 issuer-uri 在启动时做发现，Keycloak 不可达时服务会起不来。</p>
 *
 * @author 孙凯伦
 */
@AutoConfiguration(after = OAuth2ClientAutoConfiguration.class)
@ConditionalOnClass(OAuth2AuthorizedClientManager.class)
@ConditionalOnBean({ClientRegistrationRepository.class, OAuth2AuthorizedClientService.class})
@EnableConfigurationProperties(FleetOAuth2Properties.class)
public class FleetOAuth2ClientAutoConfiguration {

    /**
     * 由 Spring Boot 按自动配置加载。
     */
    public FleetOAuth2ClientAutoConfiguration() {
    }

    /**
     * 只含客户端凭证与令牌交换的已授权客户端管理器。
     *
     * @param registrations     客户端注册表
     * @param authorizedClients 已授权客户端存储（内存）
     * @param properties        utils 资源服务器与出站配置
     * @return 管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizedClientManager fleetOAuth2AuthorizedClientManager(ClientRegistrationRepository registrations,
                                                                            OAuth2AuthorizedClientService authorizedClients,
                                                                            FleetOAuth2Properties properties) {
        TokenExchangeOAuth2AuthorizedClientProvider tokenExchange = new TokenExchangeOAuth2AuthorizedClientProvider();
        tokenExchange.setSubjectTokenResolver(context -> context.getPrincipal() instanceof FleetAuthenticationToken user && user.isActive()
                ? accessToken(user.getToken()) : null);
        RestClientTokenExchangeTokenResponseClient tokenResponseClient = new RestClientTokenExchangeTokenResponseClient();
        tokenResponseClient.addParametersConverter(request -> audience(properties, request.getClientRegistration().getRegistrationId()));
        tokenExchange.setAccessTokenResponseClient(tokenResponseClient);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .provider(tokenExchange)
                .build());
        return manager;
    }

    /**
     * 出站令牌工具。
     *
     * @param authorizedClientManager 已授权客户端管理器
     * @param registrations           客户端注册表
     * @return 出站令牌工具
     */
    @Bean
    @ConditionalOnMissingBean
    public FleetOAuth2Clients fleetOAuth2Clients(OAuth2AuthorizedClientManager authorizedClientManager,
                                                 ClientRegistrationRepository registrations) {
        return new FleetOAuth2Clients(authorizedClientManager, registrations);
    }

    private static OAuth2AccessToken accessToken(Jwt jwt) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt());
    }

    private static MultiValueMap<String, String> audience(FleetOAuth2Properties properties, String registrationId) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        FleetOAuth2Properties.TokenExchange exchange = properties.getTokenExchange().get(registrationId);
        if (exchange != null && StringUtils.hasText(exchange.getAudience())) {
            parameters.add("audience", exchange.getAudience());
        }
        return parameters;
    }
}
