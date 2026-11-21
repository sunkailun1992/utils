package com.kellen.security.oauth2.client;

import com.kellen.security.oauth2.FleetAuthenticationToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 出站服务间调用带上 Keycloak 访问令牌：给 {@code RestClient} 的拦截器与给 {@code WebClient} 的过滤器，
 * 都基于 Spring Security 内置实现（{@link OAuth2ClientHttpRequestInterceptor}、{@link ServletOAuth2AuthorizedClientExchangeFilterFunction}），
 * 按 {@code spring.security.oauth2.client.registration.<注册ID>} 的授权类型取令牌：
 *
 * <ul>
 *     <li>客户端凭证：以客户端本身为主体，令牌在所有请求间共享，到期前不重复申请。</li>
 *     <li>令牌交换（RFC 8693）：以当前用户的访问令牌为 subject token，换成下游受众的令牌，按用户缓存；
 *     没有当前用户时拒绝调用。</li>
 * </ul>
 *
 * <p>任何原因没取到令牌（Keycloak 拒绝或不可达、没有当前用户）都直接报错，绝不在不带令牌的情况下调用下游。
 * 不经 {@code RestClient}/{@code WebClient} 的客户端（如 JDK {@code HttpClient}）用 {@link #bearerToken(String)} 取令牌，
 * 即 Spring Security 文档里直接使用 {@link OAuth2AuthorizedClientManager} 的写法，主体与缓存规则与上面两种相同。</p>
 *
 * @author 孙凯伦
 */
public class FleetOAuth2Clients {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    private final ClientRegistrationRepository clientRegistrations;

    /**
     * 构造出站令牌工具。
     *
     * @param authorizedClientManager 已授权客户端管理器（客户端凭证与令牌交换）
     * @param clientRegistrations     客户端注册表
     */
    public FleetOAuth2Clients(OAuth2AuthorizedClientManager authorizedClientManager, ClientRegistrationRepository clientRegistrations) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistrations = clientRegistrations;
    }

    /**
     * 给 {@code RestClient} 用的拦截器。
     *
     * @param registrationId 客户端注册 ID
     * @return 拦截器
     * @throws IllegalArgumentException 注册 ID 不存在
     */
    public ClientHttpRequestInterceptor interceptor(String registrationId) {
        ClientRegistration registration = registration(registrationId);
        OAuth2ClientHttpRequestInterceptor delegate = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        delegate.setClientRegistrationIdResolver(request -> registrationId);
        delegate.setPrincipalResolver(request -> principal(registration));
        return (request, body, execution) -> delegate.intercept(request, body, (outgoing, payload) -> {
            requireBearer(outgoing.getHeaders(), registrationId);
            return execution.execute(outgoing, payload);
        });
    }

    /**
     * 给 {@code WebClient} 用的过滤器。令牌交换的当前用户取自订阅时的安全上下文（Spring Security 在 servlet 线程订阅时写入 Reactor 上下文）。
     *
     * @param registrationId 客户端注册 ID
     * @return 过滤器
     * @throws IllegalArgumentException 注册 ID 不存在
     */
    public ExchangeFilterFunction webClientFilter(String registrationId) {
        ClientRegistration registration = registration(registrationId);
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2.setDefaultClientRegistrationId(registrationId);
        ExchangeFilterFunction principal = isTokenExchange(registration)
                ? (request, next) -> next.exchange(request)
                : ExchangeFilterFunction.ofRequestProcessor(request -> Mono.just(ClientRequest.from(request)
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.authentication(clientPrincipal(registrationId))).build()));
        ExchangeFilterFunction guard = (request, next) -> {
            requireBearer(request.headers(), registrationId);
            return next.exchange(request);
        };
        return principal.andThen(oauth2).andThen(guard);
    }

    /**
     * 给其他 HTTP 客户端取访问令牌：客户端凭证以客户端为主体、在所有请求间共享，令牌交换以当前用户为主体，都按到期时间缓存。
     *
     * @param registrationId 客户端注册 ID
     * @return 访问令牌原文，放进 {@code Authorization: Bearer} 请求头
     * @throws IllegalArgumentException     注册 ID 不存在
     * @throws ClientAuthorizationException 没取到令牌（Keycloak 拒绝、不可达或超时，令牌交换时没有当前用户）
     */
    public String bearerToken(String registrationId) {
        ClientRegistration registration = registration(registrationId);
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(principal(registration)).build();
        OAuth2AuthorizedClient authorized = authorizedClientManager.authorize(request);
        if (authorized == null || authorized.getAccessToken() == null || !StringUtils.hasText(authorized.getAccessToken().getTokenValue())) {
            throw new ClientAuthorizationException(new OAuth2Error("unauthorized_client", "未取得访问令牌，已阻止调用下游", null),
                    registrationId);
        }
        return authorized.getAccessToken().getTokenValue();
    }

    private ClientRegistration registration(String registrationId) {
        ClientRegistration registration = clientRegistrations.findByRegistrationId(registrationId);
        if (registration == null) {
            throw new IllegalArgumentException("没有配置客户端注册：" + registrationId);
        }
        return registration;
    }

    private Authentication principal(ClientRegistration registration) {
        if (!isTokenExchange(registration)) {
            return clientPrincipal(registration.getRegistrationId());
        }
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof FleetAuthenticationToken user && user.isActive()) {
            return user;
        }
        throw new ClientAuthorizationException(new OAuth2Error("invalid_request", "令牌交换需要当前用户的访问令牌", null),
                registration.getRegistrationId());
    }

    private static Authentication clientPrincipal(String registrationId) {
        return UsernamePasswordAuthenticationToken.authenticated(clientPrincipalName(registrationId), null, List.of());
    }

    /**
     * 客户端凭证令牌在已授权客户端存储里的主体名：每个注册一个，令牌在所有请求间共享。
     *
     * @param registrationId 客户端注册 ID
     * @return 主体名
     */
    static String clientPrincipalName(String registrationId) {
        return "oauth2-client:" + registrationId;
    }

    private static boolean isTokenExchange(ClientRegistration registration) {
        return AuthorizationGrantType.TOKEN_EXCHANGE.equals(registration.getAuthorizationGrantType());
    }

    private static void requireBearer(HttpHeaders headers, String registrationId) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ClientAuthorizationException(new OAuth2Error("unauthorized_client", "未取得访问令牌，已阻止调用下游", null),
                    registrationId);
        }
    }
}
