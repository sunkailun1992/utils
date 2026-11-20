package com.kellen.security.oauth2;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
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
 *     活动租户取请求头 {@code X-Tenant-Id}；用户不存在按无效令牌处理（401），停用或租户被拒生成无权限的结果（授权阶段 403），
 *     user 服务不可用抛 {@link FleetSecurityContextUnavailableException}（503）。</li>
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

    private final FleetOAuth2Properties properties;

    private final SecurityUserCache cache;

    private final ObjectProvider<SecurityContextRpcService> contextService;

    private final JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();

    /**
     * 构造转换器。
     *
     * @param properties     utils 资源服务器配置
     * @param cache          业务上下文缓存
     * @param contextService 业务上下文提供方（本服务提供时用本地实现，否则为 Dubbo 引用）
     */
    public FleetJwtAuthenticationConverter(FleetOAuth2Properties properties, SecurityUserCache cache,
                                           ObjectProvider<SecurityContextRpcService> contextService) {
        this.properties = properties;
        this.cache = cache;
        this.contextService = contextService;
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
            case OK -> new FleetAuthenticationToken(jwt, result, result.getUser().getAuthorities().stream()
                    .map(SimpleGrantedAuthority::new).toList(), userId);
            case USER_NOT_FOUND -> throw new InvalidBearerTokenException("令牌对应的用户不存在");
            case DISABLED, TENANT_DENIED -> new FleetAuthenticationToken(jwt, result, List.of(), userId);
        };
    }

    private SecurityContextResult load(String userId, String tenantId) {
        SecurityContextRpcService service = contextService.getIfAvailable();
        if (service == null) {
            return SecurityContextResult.of(SecurityContextResult.Status.USER_NOT_FOUND);
        }
        return cache.get(userId, tenantId, () -> {
            try {
                return service.resolve(userId, tenantId);
            } catch (RuntimeException ex) {
                throw new FleetSecurityContextUnavailableException("加载用户安全上下文失败：" + ex.getMessage(), ex);
            }
        });
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
