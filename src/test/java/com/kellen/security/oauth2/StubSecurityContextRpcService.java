package com.kellen.security.oauth2;

import com.kellen.security.SecurityUser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 假的 user 服务提供方：按登记的用户返回业务上下文，并按提供方的规则判断所请求的租户。
 */
public final class StubSecurityContextRpcService implements SecurityContextRpcService {

    private final Map<String, SecurityContextResult> results = new ConcurrentHashMap<>();

    private final List<String> calls = new CopyOnWriteArrayList<>();

    private volatile RuntimeException failure;

    /**
     * 登记一个启用的用户。
     *
     * @param userId           用户 ID
     * @param defaultTenantId  默认租户
     * @param deptId           部门
     * @param authorities      权限码
     * @param otherTenantIds   用户还属于的其他租户
     */
    public void enabledUser(String userId, String defaultTenantId, String deptId, List<String> authorities, String... otherTenantIds) {
        List<String> tenantIds = new java.util.ArrayList<>(List.of(defaultTenantId));
        tenantIds.addAll(List.of(otherTenantIds));
        results.put(userId, SecurityContextResult.ok(SecurityUser.builder().userId(userId).username(userId + "-name")
                .tenantId(defaultTenantId).deptId(deptId).dataScope("SELF").dataScopeDeptIds(List.of())
                .authorities(authorities).enabled(true).tenantIds(tenantIds).build()));
    }

    /**
     * 登记一个启用但没有任何租户的用户。
     *
     * @param userId 用户 ID
     */
    public void tenantlessUser(String userId) {
        results.put(userId, SecurityContextResult.ok(SecurityUser.builder().userId(userId).username(userId + "-name")
                .enabled(true).tenantIds(List.of()).build()));
    }

    /**
     * 登记一个被停用的用户。
     *
     * @param userId 用户 ID
     */
    public void disabledUser(String userId) {
        results.put(userId, SecurityContextResult.of(SecurityContextResult.Status.DISABLED));
    }

    /**
     * 之后的调用一律抛出该异常（模拟没有提供方或调用超时）。
     *
     * @param failure 异常
     */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /**
     * 清空登记与调用记录。
     */
    public void reset() {
        results.clear();
        calls.clear();
        failure = null;
    }

    /**
     * 已收到的调用，格式 {@code 用户ID|所请求的租户}。
     *
     * @return 调用记录
     */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public SecurityContextResult resolve(String userId, String requestedTenantId) {
        calls.add(userId + "|" + requestedTenantId);
        if (failure != null) {
            throw failure;
        }
        SecurityContextResult result = results.get(userId);
        if (result == null) {
            return SecurityContextResult.of(SecurityContextResult.Status.USER_NOT_FOUND);
        }
        if (result.getStatus() != SecurityContextResult.Status.OK || requestedTenantId == null) {
            return result;
        }
        SecurityUser user = result.getUser();
        if (!user.getTenantIds().contains(requestedTenantId)) {
            return SecurityContextResult.of(SecurityContextResult.Status.TENANT_DENIED);
        }
        return SecurityContextResult.ok(user.toBuilder().tenantId(requestedTenantId).build());
    }
}
