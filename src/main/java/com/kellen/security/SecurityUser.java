package com.kellen.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/**
 * 当前认证用户快照。
 *
 * <p>该对象由 {@link SecurityAuthenticationFilter} 写入 {@link UserContextHolder} 和 Spring Security 上下文，
 * 业务代码通过它读取用户、租户和权限信息。</p>
 *
 * @author 孙凯伦
 */
@Getter
@AllArgsConstructor
public class SecurityUser implements Serializable {

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 租户ID。
     */
    private String tenantId;

    /**
     * 权限码和角色码集合。
     */
    private List<String> authorities;
}
