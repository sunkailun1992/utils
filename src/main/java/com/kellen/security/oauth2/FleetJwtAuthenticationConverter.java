package com.kellen.security.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;

/**
 * 把已校验的访问令牌转成认证结果（Spring Security 资源服务器的 {@code jwtAuthenticationConverter} 扩展点）。
 *
 * <ul>
 *     <li>带 {@code user_id}：经 {@link SecurityUserCache} 取业务上下文（未命中时 Dubbo 调 user 服务），
 *     活动租户取请求头 {@code X-Tenant-Id}。用户不存在（账号未开通）、停用、租户被拒，以及 {@code user_id} 不属于令牌的 {@code sub}
 *     （user 服务登记的 Keycloak 用户对不上）都生成无权限的结果，授权阶段返回 403：令牌本身有效，按 RFC 6750 §3.1 不回 401，
 *     网关 BFF 也就不会把它当成会话失效反复重新登录。请求还没到 user 服务的连接类失败（连接断开待重连、暂无可用提供方）
 *     先按 {@link SecurityContextLookupRetry} 退避重试；仍不可用（含请求时取不到提供方）抛
 *     {@link FleetSecurityContextUnavailableException}（503）。</li>
 *     <li>没有 {@code user_id}：{@code azp} 在机器客户端名单里生成 {@link MachineAuthenticationToken}，
 *     否则生成普通令牌结果，授权阶段返回 403。</li>
 * </ul>
 *
 * @author 孙凯伦
 */
public class FleetJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * 选择活动租户的请求头。
     */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private static final Logger log = LoggerFactory.getLogger(FleetJwtAuthenticationConverter.class);

    private final FleetOAuth2Properties properties;

    private final SecurityUserCache cache;

    private final ObjectProvider<SecurityContextRpcService> contextService;

    private final JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();

    private final RetryTemplate contextRetry;

    /**
     * 构造转换器，业务上下文查询按 {@link SecurityContextLookupRetry#defaultPolicy()} 重试连接类失败。
     *
     * @param properties     utils 资源服务器配置
     * @param cache          业务上下文缓存
     * @param contextService 业务上下文提供方（本服务提供时用本地实现，否则为 Dubbo 引用）
     */
    public FleetJwtAuthenticationConverter(FleetOAuth2Properties properties, SecurityUserCache cache,
                                           ObjectProvider<SecurityContextRpcService> contextService) {
        this(properties, cache, contextService, SecurityContextLookupRetry.defaultPolicy());
    }

    /**
     * 按给定重试策略构造转换器（测试用更短的等待）。
     *
     * @param properties         utils 资源服务器配置
     * @param cache              业务上下文缓存
     * @param contextService     业务上下文提供方
     * @param contextRetryPolicy 业务上下文查询的重试策略
     */
    FleetJwtAuthenticationConverter(FleetOAuth2Properties properties, SecurityUserCache cache,
                                    ObjectProvider<SecurityContextRpcService> contextService, RetryPolicy contextRetryPolicy) {
        this.properties = properties;
        this.cache = cache;
        this.contextService = contextService;
        this.contextRetry = new RetryTemplate(contextRetryPolicy);
        this.contextRetry.setRetryListener(new ContextRetryLog());
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String userId = jwt.getClaimAsString(properties.getUserIdClaim());
        String clientId = jwt.getClaimAsString("azp");
        if (!StringUtils.hasText(userId)) {
            Collection<GrantedAuthority> scopes = scopeAuthorities.convert(jwt);
            if (clientId != null && properties.getMachineClients().contains(clientId)) {
                return new MachineAuthenticationToken(jwt, scopes, clientId);
            }
            return new JwtAuthenticationToken(jwt, List.of(), clientId == null ? jwt.getSubject() : clientId);
        }
        SecurityContextResult result = load(userId, requestedTenant());
        return switch (result.getStatus()) {
            case OK -> belongsToSubject(jwt, result, userId)
                    ? new FleetAuthenticationToken(jwt, result, result.getUser().getAuthorities().stream()
                    .map(SimpleGrantedAuthority::new).toList(), userId)
                    : new FleetAuthenticationToken(jwt, SecurityContextResult.of(SecurityContextResult.Status.SUBJECT_MISMATCH),
                    List.of(), userId);
            case USER_NOT_FOUND, DISABLED, TENANT_DENIED, SUBJECT_MISMATCH -> new FleetAuthenticationToken(jwt, result, List.of(), userId);
        };
    }

    /**
     * 令牌里的 {@code user_id} 是否属于令牌的 {@code sub}：OIDC Core §5.7 规定 iss + sub 才是终端用户的稳定标识，
     * user 服务登记了每个用户对应的 Keycloak 用户（{@link com.kellen.security.SecurityUser#getIdpSubject()}），两者必须一致。
     * 提供方没给（2.0.7 及更早的 user 服务、滚动升级期间的缓存副本）时不核对，记一行警告。不记录令牌与 sub。
     */
    private static boolean belongsToSubject(Jwt jwt, SecurityContextResult result, String userId) {
        String idpSubject = result.getUser().getIdpSubject();
        if (!StringUtils.hasText(idpSubject)) {
            log.warn("业务上下文没有登记 Keycloak 用户，本次未核对令牌的 sub：user_id={}", userId);
            return true;
        }
        if (idpSubject.equals(jwt.getSubject())) {
            return true;
        }
        log.warn("访问令牌的 sub 与 user 服务登记的 Keycloak 用户不一致，拒绝访问：user_id={}", userId);
        return false;
    }

    private SecurityContextResult load(String userId, String tenantId) {
        SecurityContextRpcService service = contextService.getIfAvailable();
        if (service == null) {
            throw new FleetSecurityContextUnavailableException("没有业务上下文提供方 SecurityContextRpcService", null);
        }
        return cache.get(userId, tenantId, () -> resolve(service, userId, tenantId));
    }

    /**
     * 向提供方查询业务上下文，连接类失败按退避重试；仍失败时按 user 服务不可用（503）处理，消息写明尝试次数与最后一次原因。
     */
    private SecurityContextResult resolve(SecurityContextRpcService service, String userId, String tenantId) {
        try {
            return contextRetry.execute(() -> service.resolve(userId, tenantId));
        } catch (RetryException ex) {
            Throwable last = ex.getCause();
            if (last instanceof Error error) {
                throw error;
            }
            int attempts = ex.getRetryCount() + 1;
            String prefix = attempts > 1 ? "加载用户安全上下文失败（共尝试 " + attempts + " 次）：" : "加载用户安全上下文失败：";
            throw new FleetSecurityContextUnavailableException(prefix + (last == null ? ex.getMessage() : last.getMessage()), last);
        }
    }

    /**
     * 重试后查询成功时记一行，留下连接类失败确实发生过的证据（不记令牌与业务上下文内容）。
     */
    private static final class ContextRetryLog implements RetryListener {

        @Override
        public void onRetryableExecution(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
            if (retryState.isSuccessful() && retryState.getRetryCount() > 0) {
                Throwable first = NestedExceptionUtils.getMostSpecificCause(retryState.getExceptions().get(0));
                log.info("加载用户安全上下文重试 {} 次后成功，第一次失败：{}：{}", retryState.getRetryCount(),
                        first.getClass().getSimpleName(), first.getMessage());
            }
        }
    }

    private static String requestedTenant() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            String tenantId = servlet.getRequest().getHeader(TENANT_HEADER);
            return StringUtils.hasText(tenantId) ? tenantId.trim() : null;
        }
        return null;
    }
}
