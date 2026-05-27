package com.kellen.security;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 当前线程用户上下文持有器。
 *
 * <p>使用 TransmittableThreadLocal 是为了兼容线程池异步执行时的上下文传递，
 * 请求结束必须调用 {@link #clear()} 清理上下文。</p>
 *
 * @author 孙凯伦
 */
public class UserContextHolder {

    /**
     * 当前用户上下文。
     */
    private static final TransmittableThreadLocal<SecurityUser> USER_CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private UserContextHolder() {
    }

    /**
     * 写入当前认证用户。
     *
     * @param user 当前认证用户
     */
    public static void set(SecurityUser user) {
        USER_CONTEXT.set(user); // 将用户写入当前线程上下文。
    }

    /**
     * 获取当前认证用户。
     *
     * @return 当前认证用户，未认证时返回 null
     */
    public static SecurityUser get() {
        return USER_CONTEXT.get(); // 从当前线程上下文读取用户。
    }

    /**
     * 获取当前用户ID。
     *
     * @return 当前用户ID，未认证时返回 null
     */
    public static String getUserId() {
        SecurityUser user = get(); // 先读取当前用户。
        return user == null ? null : user.getUserId(); // 用户不存在时返回 null。
    }

    /**
     * 获取当前租户ID。
     *
     * @return 当前租户ID，未认证或无租户时返回 null
     */
    public static String getTenantId() {
        SecurityUser user = get(); // 先读取当前用户。
        return user == null ? null : user.getTenantId(); // 用户不存在时返回 null。
    }

    /**
     * 清理当前用户上下文。
     */
    public static void clear() {
        USER_CONTEXT.remove(); // 移除线程变量，避免线程复用串用户。
    }
}
