package com.kellen.security.oauth2;

/**
 * 按用户加载认证横切所需业务上下文的 Dubbo 契约，由 user 服务提供，各资源服务器调用。
 *
 * <p>访问令牌只证明身份（{@code user_id}），租户、部门、数据权限、权限码、角色与是否停用都以 user 服务为准。
 * 契约放在 utils 的安全包而不放 rpc-api：utils 不依赖 rpc-api，且契约只含认证横切所需字段。</p>
 *
 * @author 孙凯伦
 */
public interface SecurityContextRpcService {

    /**
     * 加载用户的业务上下文，并按提供方规则确定本次请求的活动租户。
     *
     * @param userId            访问令牌里的业务用户 ID
     * @param requestedTenantId 请求头 {@code X-Tenant-Id} 指定的租户；为空时取用户的默认租户
     * @return 加载结果：用户不存在、已停用或无权访问所请求租户时返回对应状态，不抛业务异常
     */
    SecurityContextResult resolve(String userId, String requestedTenantId);
}
