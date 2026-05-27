package com.kellen.utils.datasource;

import com.kellen.utils.context.DynamicSourceTtl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 动态数据源工具类。
 *
 * <p>本工具只封装动态数据源上下文的基础读写能力，当前公共包只保留 master 与 gray 两个数据源。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
public class DataSourceUtil {

    /**
     * 数据源请求头名称。
     */
    public final static String DATA_SOURCE = "dataSource";

    /**
     * 根据请求头数据源值获取实际数据源。
     *
     * @param requestDataSource 请求头中的数据源名称
     * @return 实际使用的数据源名称
     */
    public static String getDataSource(String requestDataSource) {
        if (StringUtils.isNotBlank(requestDataSource)) {
            log.debug("初始化动态数据源，请求头数据源：{}", requestDataSource); // 请求显式指定数据源时按请求上下文使用。
            return requestDataSource;
        }
        return DynamicSourceTtl.MASTER_DATASOURCE; // 请求未指定数据源时回落到主库。
    }

    /**
     * 写入当前线程数据源。
     *
     * @param dataSource 数据源名称
     */
    public static void put(String dataSource) {
        DynamicSourceTtl.push(dataSource); // 统一委托给 TTL 上下文，保证线程池场景可透传。
    }

    /**
     * 清除当前线程数据源。
     */
    public static void clear() {
        DynamicSourceTtl.clear(); // 请求结束或异常时必须清理，避免线程复用串数据源。
    }

    /**
     * 获取当前线程数据源。
     *
     * @return 当前线程数据源名称
     */
    public static String get() {
        return DynamicSourceTtl.get(); // 返回当前上下文中的数据源，可能为空。
    }

}
