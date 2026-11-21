package com.kellen.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 当前认证用户的业务上下文快照。
 *
 * <p>身份由 Keycloak 签发的访问令牌证明（令牌只带 {@code user_id}），租户、部门、数据权限、权限码、角色与是否停用
 * 由 user 服务经 {@code SecurityContextRpcService} 按请求提供，资源服务器把它写入 {@link UserContextHolder}、
 * 租户上下文与 Spring Security 的权限集合。业务代码通过它读取用户、租户和权限信息。</p>
 *
 * <p>同一对象也经 Dubbo 传输、以 JSON 存入 Redis 缓存，因此字段只含认证横切所需的信息。</p>
 *
 * @author 孙凯伦
 */
@Getter
public class SecurityUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * 用户ID。
     */
    private final String userId;

    /**
     * 用户名。
     */
    private final String username;

    /**
     * 本次请求的活动租户ID。
     */
    private final String tenantId;

    /**
     * 部门ID。
     */
    private final String deptId;

    /**
     * 数据权限范围。
     */
    private final String dataScope;

    /**
     * 数据权限部门ID集合。
     */
    private final List<String> dataScopeDeptIds;

    /**
     * 权限码和角色码集合（角色带 {@code ROLE_} 前缀）。
     */
    private final List<String> authorities;

    /**
     * 账号是否启用；停用的账号即使持有有效令牌也返回 403。
     */
    private final boolean enabled;

    /**
     * 用户所属的全部租户ID，活动租户只能从中选择。
     */
    private final List<String> tenantIds;

    /**
     * user 服务给该用户登记的 Keycloak 用户 ID（访问令牌的 {@code sub}）；资源服务器据此核对令牌里的 {@code user_id}
     * 确实属于这个 Keycloak 账号（OIDC Core §5.7：iss + sub 才是终端用户的稳定标识）。2.0.8 起由提供方填写，
     * 旧版提供方或按消息还原的上下文为空。
     */
    private final String idpSubject;

    /**
     * 构造完整的安全用户快照；空集合按空列表处理。
     *
     * @param userId           用户ID
     * @param username         用户名
     * @param tenantId         活动租户ID
     * @param deptId           部门ID
     * @param dataScope        数据权限范围
     * @param dataScopeDeptIds 数据权限部门ID集合
     * @param authorities      权限码和角色码集合
     * @param enabled          账号是否启用
     * @param tenantIds        用户所属的全部租户ID
     * @param idpSubject       user 服务登记的 Keycloak 用户 ID（访问令牌的 sub），可为空
     */
    @Builder(toBuilder = true)
    @JsonCreator
    public SecurityUser(@JsonProperty("userId") String userId,
                        @JsonProperty("username") String username,
                        @JsonProperty("tenantId") String tenantId,
                        @JsonProperty("deptId") String deptId,
                        @JsonProperty("dataScope") String dataScope,
                        @JsonProperty("dataScopeDeptIds") List<String> dataScopeDeptIds,
                        @JsonProperty("authorities") List<String> authorities,
                        @JsonProperty("enabled") boolean enabled,
                        @JsonProperty("tenantIds") List<String> tenantIds,
                        @JsonProperty("idpSubject") String idpSubject) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.deptId = deptId;
        this.dataScope = dataScope;
        this.dataScopeDeptIds = copy(dataScopeDeptIds);
        this.authorities = copy(authorities);
        this.enabled = enabled;
        this.tenantIds = copy(tenantIds);
        this.idpSubject = idpSubject;
    }

    /**
     * 构造不带 Keycloak 用户 ID 的完整快照（2.0.7 及更早的写法，保留源码兼容）。
     *
     * @param userId           用户ID
     * @param username         用户名
     * @param tenantId         活动租户ID
     * @param deptId           部门ID
     * @param dataScope        数据权限范围
     * @param dataScopeDeptIds 数据权限部门ID集合
     * @param authorities      权限码和角色码集合
     * @param enabled          账号是否启用
     * @param tenantIds        用户所属的全部租户ID
     */
    public SecurityUser(String userId, String username, String tenantId, String deptId, String dataScope,
                        List<String> dataScopeDeptIds, List<String> authorities, boolean enabled, List<String> tenantIds) {
        this(userId, username, tenantId, deptId, dataScope, dataScopeDeptIds, authorities, enabled, tenantIds, null);
    }

    /**
     * 构造只含当前请求上下文的快照（如按消息属性还原的上下文）：视为启用，所属租户只有活动租户。
     *
     * @param userId           用户ID
     * @param username         用户名
     * @param tenantId         活动租户ID
     * @param deptId           部门ID
     * @param dataScope        数据权限范围
     * @param dataScopeDeptIds 数据权限部门ID集合
     * @param authorities      权限码和角色码集合
     */
    public SecurityUser(String userId, String username, String tenantId, String deptId, String dataScope,
                        List<String> dataScopeDeptIds, List<String> authorities) {
        this(userId, username, tenantId, deptId, dataScope, dataScopeDeptIds, authorities, true,
                tenantId == null ? List.of() : List.of(tenantId));
    }

    /**
     * 构造不带部门与数据范围的快照。
     *
     * @param userId      用户ID
     * @param username    用户名
     * @param tenantId    活动租户ID
     * @param authorities 权限码和角色码集合
     * @author sunkailun
     */
    public SecurityUser(String userId, String username, String tenantId, List<String> authorities) {
        this(userId, username, tenantId, null, null, new ArrayList<>(), authorities);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null).toList();
    }
}
