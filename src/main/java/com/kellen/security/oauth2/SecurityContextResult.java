package com.kellen.security.oauth2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kellen.security.SecurityUser;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * {@link SecurityContextRpcService#resolve} 的结果：状态，以及状态为 {@link Status#OK} 时的用户上下文。
 *
 * <p>经 Dubbo 传输、以 JSON 存入 Redis，写成可序列化的普通类，与 rpc-api 的契约对象一致。</p>
 *
 * @author 孙凯伦
 */
public final class SecurityContextResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 加载状态。
     */
    private final Status status;

    /**
     * 用户上下文，只在状态为 {@link Status#OK} 时有值。
     */
    private final SecurityUser user;

    /**
     * 构造加载结果。
     *
     * @param status 加载状态
     * @param user   用户上下文，状态不是 OK 时忽略
     */
    @JsonCreator
    public SecurityContextResult(@JsonProperty("status") Status status, @JsonProperty("user") SecurityUser user) {
        this.status = Objects.requireNonNull(status, "status");
        this.user = status == Status.OK ? Objects.requireNonNull(user, "user") : null;
    }

    /**
     * 加载成功。
     *
     * @param user 用户上下文
     * @return 加载结果
     */
    public static SecurityContextResult ok(SecurityUser user) {
        return new SecurityContextResult(Status.OK, user);
    }

    /**
     * 不带用户上下文的结果（用户不存在、已停用或无权访问所请求租户）。
     *
     * @param status 加载状态，不能是 OK
     * @return 加载结果
     */
    public static SecurityContextResult of(Status status) {
        if (status == Status.OK) {
            throw new IllegalArgumentException("加载成功的结果必须带用户上下文");
        }
        return new SecurityContextResult(status, null);
    }

    /**
     * 加载状态。
     *
     * @return 加载状态
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 用户上下文。
     *
     * @return 用户上下文；状态不是 OK 时为 null
     */
    public SecurityUser getUser() {
        return user;
    }

    /**
     * 加载状态。
     */
    public enum Status {
        /**
         * 加载成功。
         */
        OK,
        /**
         * 令牌里的用户在 user 服务不存在（返回 401）。
         */
        USER_NOT_FOUND,
        /**
         * 账号已停用（返回 403）。
         */
        DISABLED,
        /**
         * 用户不属于请求头指定的租户（返回 403）。
         */
        TENANT_DENIED
    }
}
