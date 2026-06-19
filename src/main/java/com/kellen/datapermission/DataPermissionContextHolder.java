package com.kellen.datapermission;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 数据权限线程上下文。
 *
 * @author sunkailun
 */
public class DataPermissionContextHolder {

    /**
     * 忽略数据权限标记。
     */
    private static final TransmittableThreadLocal<Boolean> IGNORE_CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 私有构造方法。
     *
     * @author sunkailun
     */
    private DataPermissionContextHolder() {
    }

    /**
     * 标记当前线程忽略数据权限。
     *
     * @return void
     * @author sunkailun
     */
    public static void ignore() {
        IGNORE_CONTEXT.set(Boolean.TRUE); // 当前线程后续 SQL 不追加数据权限条件。
    }

    /**
     * 判断当前线程是否忽略数据权限。
     *
     * @return true 表示忽略数据权限
     * @author sunkailun
     */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE_CONTEXT.get()); // 只把显式 true 识别为忽略，避免空值误判。
    }

    /**
     * 清理当前线程忽略数据权限标记。
     *
     * @return void
     * @author sunkailun
     */
    public static void clear() {
        IGNORE_CONTEXT.remove(); // 请求或业务结束后清理线程变量，避免线程复用串权限。
    }
}
