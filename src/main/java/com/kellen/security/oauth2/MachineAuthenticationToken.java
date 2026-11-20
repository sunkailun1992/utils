package com.kellen.security.oauth2;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.Serial;
import java.util.Collection;

/**
 * 机器调用的认证结果：客户端凭证令牌，没有 {@code user_id}，{@code azp} 在 {@code fleet.security.oauth2.machine-clients} 里。
 *
 * <p>名称为客户端 ID（{@code azp}），权限为令牌作用域（{@code SCOPE_} 前缀）；不写用户与租户上下文。</p>
 *
 * @author 孙凯伦
 */
public class MachineAuthenticationToken extends JwtAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造机器认证结果。
     *
     * @param jwt         已校验的访问令牌
     * @param authorities 作用域权限
     * @param clientId    客户端 ID（azp）
     */
    public MachineAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities, String clientId) {
        super(jwt, authorities, clientId);
    }
}
