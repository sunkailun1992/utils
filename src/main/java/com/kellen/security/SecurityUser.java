package com.kellen.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
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
     * 部门ID。
     */
    private String deptId;

    /**
     * 数据权限范围。
     */
    private String dataScope;

    /**
     * 数据权限部门ID集合。
     */
    private List<String> dataScopeDeptIds;

    /**
     * 权限码和角色码集合。
     */
    private List<String> authorities;

    /**
     * 构造兼容旧调用方的安全用户快照。
     *
     * @param userId      用户ID
     * @param username    用户名
     * @param tenantId    租户ID
     * @param authorities 权限码和角色码集合
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    public SecurityUser(String userId, String username, String tenantId, List<String> authorities) {
        this(userId, username, tenantId, null, null, new ArrayList<>(), authorities); // 旧构造方法默认不携带部门和数据范围。
    }
}
