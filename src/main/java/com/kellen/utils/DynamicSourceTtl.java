package com.kellen.utils;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


/**
 * 动态数据源上下文管理工具。
 *
 * <p>同时维护 TransmittableThreadLocal 和 dynamic-datasource 的上下文，
 * 用于普通请求、异步线程和 Feign/Rabbit 等链路里保持数据源一致。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
public class DynamicSourceTtl {

    /**
     * 灰度数据源名称。
     */
    public final static String SLAVE_DATASOURCE = "gray";

    /**
     * 主数据源名称。
     */
    public final static String MASTER_DATASOURCE = "master";

    /**
     * 当前线程数据源上下文。
     */
    public static TransmittableThreadLocal<String> dataSourceContext = new TransmittableThreadLocal<>();

    /**
     * 写入当前线程数据源。
     *
     * @param dataSource 数据源名称
     * @return 实际写入的数据源名称
     */
    public static String push(String dataSource) {
        String ds = StringUtils.isBlank(dataSource) ? MASTER_DATASOURCE : dataSource; // 缺少数据源时回退主库。
        dataSourceContext.set(ds); // 写入 TTL 上下文，支持线程池传递。
        DynamicDataSourceContextHolder.push(ds); // 写入 dynamic-datasource 上下文，供数据源路由使用。
        return ds; // 返回最终生效的数据源。
    }

    /**
     * 清理当前线程数据源上下文。
     */
    public static void clear() {
        dataSourceContext.remove(); // 清理 TTL 数据源，避免线程复用串数据源。
        DynamicDataSourceContextHolder.clear(); // 清理 dynamic-datasource 数据源栈。
    }

    /**
     * 获取当前线程数据源。
     *
     * @return 当前数据源名称
     */
    public static String get() {
        // 判断如果 TTL 有参数而 dynamic-datasource 未找到参数，则重新补写 dynamic-datasource 上下文。
        if (StringUtils.isNotBlank(dataSourceContext.get()) && StringUtils.isBlank(DynamicDataSourceContextHolder.peek())) {
            DynamicDataSourceContextHolder.push(dataSourceContext.get()); // 以 TTL 中的数据源为准补写数据源路由上下文。
        }
        // 判断 TTL 和 dynamic-datasource 都有参数但不一致时，以 TTL 为准重新写入。
        if (StringUtils.isNotBlank(dataSourceContext.get()) && StringUtils.isNotBlank(DynamicDataSourceContextHolder.peek())) {
            if (!dataSourceContext.get().equals(DynamicDataSourceContextHolder.peek())) {
                DynamicDataSourceContextHolder.push(dataSourceContext.get()); // 修正 dynamic-datasource 当前数据源。
            }
        }
        // 判断是否不存在环境标识。
        if (dataSourceContext.get() == null) {
            log.info("环境标识为空，Ttl赋值默认参数：{}", MASTER_DATASOURCE); // 记录默认数据源兜底。
            dataSourceContext.set(MASTER_DATASOURCE); // TTL 无数据源时回退主库。
        }
        return dataSourceContext.get(); // 返回当前线程最终数据源。
    }
}
