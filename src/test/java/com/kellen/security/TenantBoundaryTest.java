package com.kellen.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求点名的目标租户与活动租户的边界：只能是自己的活动租户，平台超级管理员可以点名任一租户；
 * 空值、没有当前用户、只差空白或大小写的写法都拒绝。
 */
class TenantBoundaryTest {

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void activeTenantIsAccessible() {
        signIn("t1", "user:auth:manage");
        assertThat(TenantBoundary.canAccess("t1")).isTrue();
        assertThat(TenantBoundary.require("t1")).isEqualTo("t1");
    }

    @Test
    void anotherTenantIsDeniedEvenWithManageAuthority() {
        signIn("t1", "user:auth:manage", "ROLE_TENANT_ADMIN");
        assertThat(TenantBoundary.canAccess("t2")).isFalse();
        assertThatThrownBy(() -> TenantBoundary.require("t2")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void platformSuperAdminMayTargetAnyTenant() {
        signIn("t1", TenantBoundary.PLATFORM_SUPER_ADMIN);
        assertThat(TenantBoundary.isPlatformSuperAdmin()).isTrue();
        assertThat(TenantBoundary.require("t2")).isEqualTo("t2");
        assertThat(TenantBoundary.require("t1")).isEqualTo("t1");
    }

    @Test
    void blankTargetIsDeniedEvenForPlatformSuperAdmin() {
        signIn("t1", TenantBoundary.PLATFORM_SUPER_ADMIN);
        for (String target : new String[]{null, "", "  "}) {
            assertThat(TenantBoundary.canAccess(target)).as("[%s]", target).isFalse();
            assertThatThrownBy(() -> TenantBoundary.require(target)).as("[%s]", target).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void callerWithoutBusinessContextIsDenied() {
        assertThat(TenantBoundary.isPlatformSuperAdmin()).isFalse();
        assertThat(TenantBoundary.canAccess("t1")).isFalse();
        assertThatThrownBy(() -> TenantBoundary.require("t1")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nearMissesOfTheActiveTenantOrAdminAuthorityAreNotAccepted() {
        signIn("t1", "role_platform_super_admin", "PLATFORM_SUPER_ADMIN");
        assertThat(TenantBoundary.isPlatformSuperAdmin()).isFalse();
        for (String target : new String[]{" t1", "t1 ", "T1"}) {
            assertThat(TenantBoundary.canAccess(target)).as("[%s]", target).isFalse();
        }
    }

    @Test
    void userWithoutAuthoritiesStillReachesOnlyTheActiveTenant() {
        UserContextHolder.set(new SecurityUser("u1", "u1", "t1", null));
        assertThat(TenantBoundary.isPlatformSuperAdmin()).isFalse();
        assertThat(TenantBoundary.canAccess("t1")).isTrue();
        assertThat(TenantBoundary.canAccess("t2")).isFalse();
    }

    private static void signIn(String activeTenant, String... authorities) {
        UserContextHolder.set(new SecurityUser("u1", "u1", activeTenant, List.of(authorities)));
    }
}
