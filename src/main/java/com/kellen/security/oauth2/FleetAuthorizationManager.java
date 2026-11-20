package com.kellen.security.oauth2;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.function.Supplier;

/**
 * 受保护路径的授权：令牌有效之外，还要求业务上下文可用的用户，或名单里的机器客户端；
 * 配了 {@code required-azp} 时只放行这些客户端的令牌。其余已认证请求返回 403，未认证返回 401。
 *
 * @author 孙凯伦
 */
final class FleetAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final List<String> requiredAzp;

    /**
     * 构造授权器。
     *
     * @param requiredAzp 只接受的客户端，空表示不限制
     */
    FleetAuthorizationManager(List<String> requiredAzp) {
        this.requiredAzp = List.copyOf(requiredAzp);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        boolean granted = switch (current) {
            case FleetAuthenticationToken user -> user.isActive() && azpAllowed(user);
            case MachineAuthenticationToken machine -> azpAllowed(machine);
            case null, default -> false;
        };
        return new AuthorizationDecision(granted);
    }

    private boolean azpAllowed(JwtAuthenticationToken token) {
        return requiredAzp.isEmpty() || requiredAzp.contains(token.getToken().getClaimAsString("azp"));
    }
}
