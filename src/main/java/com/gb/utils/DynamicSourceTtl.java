package com.gb.utils;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


/**
 * @author: ranyang
 * @Date: 2021/07/30 09:09
 * @descript: 动态数据源管理
 */
@Slf4j
public class DynamicSourceTtl {
    //灰度数据源
    public final static String SLAVE_DATASOURCE = "gray";
    //银行数据源
    public final static String BANK_DATASOURCE = "bank";
    //汇中数据源
    public final static String HZ_DATASOURCE = "hz";
    //主数据源
    public final static String MASTER_DATASOURCE = "master";
    public static TransmittableThreadLocal<String> dataSourceContext = new TransmittableThreadLocal<>();

    public static String push(String dataSource) {
        String ds = StringUtils.isBlank(dataSource) ? MASTER_DATASOURCE : dataSource;
        dataSourceContext.set(ds);
        DynamicDataSourceContextHolder.push(ds);
        return ds;
    }

    public static void clear() {
        dataSourceContext.remove();
        DynamicDataSourceContextHolder.clear();
    }

    public static String get() {
        //判断如果ttl有参数，ddsch未找到参数。重新赋值
        if (StringUtils.isNotBlank(dataSourceContext.get()) && StringUtils.isBlank(DynamicDataSourceContextHolder.peek())) {
            DynamicDataSourceContextHolder.push(dataSourceContext.get());
        }
        //判断ttl有参数，并ddsch有参数，但互相参数不一致，以ttl为准重新赋值
        if (StringUtils.isNotBlank(dataSourceContext.get()) && StringUtils.isNotBlank(DynamicDataSourceContextHolder.peek())) {
            if (!dataSourceContext.get().equals(DynamicDataSourceContextHolder.peek())) {
                DynamicDataSourceContextHolder.push(dataSourceContext.get());
            }
        }
        //判断是否不存在环境标识
        if (dataSourceContext.get() == null) {
            log.info("环境标识为空，Ttl赋值默认参数：" + MASTER_DATASOURCE);
            dataSourceContext.set(MASTER_DATASOURCE);
        }
        return dataSourceContext.get();
    }
}
