package com.kellen.utils;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.apache.commons.lang3.StringUtils;

public class TenantContextHolder {

    private static final TransmittableThreadLocal<String> TENANT_CONTEXT = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Boolean> IGNORE_CONTEXT = new TransmittableThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            TENANT_CONTEXT.remove();
            return;
        }
        TENANT_CONTEXT.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_CONTEXT.get();
    }

    public static boolean hasTenantId() {
        return StringUtils.isNotBlank(getTenantId());
    }

    public static void ignore() {
        IGNORE_CONTEXT.set(Boolean.TRUE);
    }

    public static void clearIgnore() {
        IGNORE_CONTEXT.remove();
    }

    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE_CONTEXT.get());
    }

    public static void clear() {
        TENANT_CONTEXT.remove();
        IGNORE_CONTEXT.remove();
    }
}
