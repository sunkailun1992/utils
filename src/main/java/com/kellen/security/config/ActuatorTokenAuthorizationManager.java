package com.kellen.security.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * 按 {@code actuatorReq} 请求头校验 Actuator 访问令牌的授权器。
 *
 * <p>只认令牌，不认登录用户或网关透传身份头：内网直连实例端口的调用方可以自行构造这些头。
 * 令牌未配置时一律拒绝，避免空请求头与空配置值相等被放行；比较使用常量时间算法，防止按响应耗时逐位猜测令牌。
 * 每次请求都读取当前配置值，Nacos 刷新轮换令牌后无需重启即可生效。</p>
 *
 * @author 孙凯伦
 */
final class ActuatorTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * 运维调用方携带 Actuator 令牌的请求头，沿用历史拦截器约定的名称。
     */
    static final String TOKEN_HEADER = "actuatorReq";

    /**
     * 当前配置的访问令牌，空白表示未配置。
     */
    private final Supplier<String> configuredToken;

    /**
     * 构造 Actuator 令牌授权器。
     *
     * @param configuredToken 当前配置的访问令牌，空白表示未配置
     */
    ActuatorTokenAuthorizationManager(Supplier<String> configuredToken) {
        this.configuredToken = configuredToken;
    }

    /**
     * 判断当前请求是否携带了配置的 Actuator 令牌。
     *
     * @param authentication 当前认证信息，Actuator 授权不使用
     * @param context        当前请求上下文
     * @return 令牌匹配时授权通过
     */
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(tokenMatches(context.getRequest().getHeader(TOKEN_HEADER)));
    }

    private boolean tokenMatches(String providedToken) {
        String expectedToken = configuredToken.get();
        if (!StringUtils.hasText(expectedToken) || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8), providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
