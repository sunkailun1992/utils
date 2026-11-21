package com.kellen.security;

import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * 接口参数或请求体里点名的目标租户与当前用户活动租户的边界。
 *
 * <p>活动租户由资源服务器按请求头 {@code X-Tenant-Id} 经 user 服务核实后写入 {@link UserContextHolder}，
 * 权限码与数据权限都按它计算。接口另带的 {@code tenantId} 只能等于活动租户；平台超级管理员
 * （{@link #PLATFORM_SUPER_ADMIN}，管理员分类与租户无关）可以点名任一租户。做法同芋道的
 * {@code TenantSecurityWebFilter}：登录用户的租户与请求的租户不一致就拒绝；依据 OWASP API Security Top 10 2023
 * 的 API1（对象级授权）。</p>
 *
 * <p>服务在把请求里的租户写进 {@code TenantContextHolder} 之前先调 {@link #require(String)}；
 * 不满足时抛 {@link AccessDeniedException}，由 Spring Security 回 403。</p>
 *
 * @author 孙凯伦
 */
public final class TenantBoundary {

    /**
     * 平台超级管理员的权限（user 服务把管理员分类映射成的角色）。
     */
    public static final String PLATFORM_SUPER_ADMIN = "ROLE_PLATFORM_SUPER_ADMIN";

    private TenantBoundary() {
    }

    /**
     * 当前用户是否平台超级管理员。
     *
     * @return 没有当前用户时为 false
     */
    public static boolean isPlatformSuperAdmin() {
        SecurityUser user = UserContextHolder.get();
        List<String> authorities = user == null ? null : user.getAuthorities();
        return authorities != null && authorities.contains(PLATFORM_SUPER_ADMIN);
    }

    /**
     * 当前用户能否在目标租户上操作：目标是自己的活动租户（逐字相等），或自己是平台超级管理员。
     *
     * @param tenantId 目标租户
     * @return 目标为空或没有当前用户时为 false
     */
    public static boolean canAccess(String tenantId) {
        SecurityUser user = UserContextHolder.get();
        if (tenantId == null || tenantId.isBlank() || user == null) {
            return false;
        }
        return tenantId.equals(user.getTenantId()) || isPlatformSuperAdmin();
    }

    /**
     * 校验并返回目标租户。
     *
     * @param tenantId 目标租户
     * @return 原样返回目标租户
     * @throws AccessDeniedException 目标为空、没有当前用户或越出活动租户时
     */
    public static String require(String tenantId) {
        if (!canAccess(tenantId)) {
            throw new AccessDeniedException("无权访问所请求的租户");
        }
        return tenantId;
    }
}
