package com.kellen.utils.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.apache.commons.lang3.StringUtils;

/**
 * 当前租户上下文持有器。
 *
 * <p>租户上下文主要供 MyBatis-Plus 租户插件、Dubbo 透传和业务代码读取。
 * 请求结束或跨租户逻辑结束后必须调用 {@link #clear()} 或 {@link #clearIgnore()}。</p>
 *
 * @author 孙凯伦
 */
public class TenantContextHolder {

    /**
     * 当前租户ID上下文。
     */
    private static final TransmittableThreadLocal<String> TENANT_CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 是否忽略租户条件上下文。
     */
    private static final TransmittableThreadLocal<Boolean> IGNORE_CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private TenantContextHolder() {
    }

    /**
     * 设置当前租户ID。
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            TENANT_CONTEXT.remove(); // 空租户等价于清理租户上下文。
            return;
        }
        TENANT_CONTEXT.set(tenantId); // 写入当前线程租户ID。
    }

    /**
     * 获取当前租户ID。
     *
     * @return 当前租户ID，未设置时返回 null
     */
    public static String getTenantId() {
        return TENANT_CONTEXT.get(); // 从当前线程上下文读取租户ID。
    }

    /**
     * 判断当前是否存在租户ID。
     *
     * @return true 表示存在租户ID
     */
    public static boolean hasTenantId() {
        return StringUtils.isNotBlank(getTenantId()); // 租户ID非空才视为存在租户上下文。
    }

    /**
     * 标记当前线程忽略租户条件。
     */
    public static void ignore() {
        IGNORE_CONTEXT.set(Boolean.TRUE); // 写入忽略标记，供租户插件跳过 SQL 条件拼接。
    }

    /**
     * 清理忽略租户标记。
     */
    public static void clearIgnore() {
        IGNORE_CONTEXT.remove(); // 只清理忽略标记，不影响租户ID。
    }

    /**
     * 判断当前线程是否忽略租户条件。
     *
     * @return true 表示忽略租户条件
     */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE_CONTEXT.get()); // 只有显式 TRUE 才忽略租户条件。
    }

    /**
     * 清理当前线程租户上下文。
     */
    public static void clear() {
        TENANT_CONTEXT.remove(); // 清理租户ID。
        IGNORE_CONTEXT.remove(); // 清理忽略租户标记。
    }
}
