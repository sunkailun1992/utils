package com.kellen.security;

import com.alibaba.ttl.TransmittableThreadLocal;

public class UserContextHolder {

    private static final TransmittableThreadLocal<SecurityUser> USER_CONTEXT = new TransmittableThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(SecurityUser user) {
        USER_CONTEXT.set(user);
    }

    public static SecurityUser get() {
        return USER_CONTEXT.get();
    }

    public static String getUserId() {
        SecurityUser user = get();
        return user == null ? null : user.getUserId();
    }

    public static String getTenantId() {
        SecurityUser user = get();
        return user == null ? null : user.getTenantId();
    }

    public static void clear() {
        USER_CONTEXT.remove();
    }
}
