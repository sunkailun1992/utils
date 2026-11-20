package com.kellen.security.oauth2;

import com.kellen.security.SecurityUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.Serial;
import java.util.Collection;

/**
 * 用户访问令牌的认证结果：令牌本身加上 user 服务给出的业务上下文。
 *
 * <p>名称为业务用户 ID（{@code user_id}），权限为 user 服务给出的权限码与角色；令牌原文仍可经 {@link #getToken()}
 * 取得，供出站令牌交换作为 subject token。业务上下文状态不是 OK（停用、租户被拒）时没有权限，授权阶段返回 403。</p>
 *
 * @author 孙凯伦
 */
public class FleetAuthenticationToken extends JwtAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务上下文加载结果。
     */
    private final SecurityContextResult result;

    /**
     * 构造用户认证结果。
     *
     * @param jwt         已校验的访问令牌
     * @param result      业务上下文加载结果
     * @param authorities 权限集合
     * @param userId      业务用户 ID
     */
    public FleetAuthenticationToken(Jwt jwt, SecurityContextResult result, Collection<? extends GrantedAuthority> authorities,
                                    String userId) {
        super(jwt, authorities, userId);
        this.result = result;
    }

    /**
     * 业务上下文加载状态。
     *
     * @return 加载状态
     */
    public SecurityContextResult.Status getStatus() {
        return result.getStatus();
    }

    /**
     * 业务上下文；状态不是 OK 时为 null。
     *
     * @return 业务上下文
     */
    public SecurityUser getUser() {
        return result.getUser();
    }

    /**
     * 业务上下文是否可用（用户存在、启用且可访问所请求的租户）。
     *
     * @return true 表示可用
     */
    public boolean isActive() {
        return result.getStatus() == SecurityContextResult.Status.OK;
    }
}
