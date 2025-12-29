package com.gb.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 数据源工具类
 * @author sunx
 * @date 2021/6/30 14:30
 */
@Slf4j
public class DataSourceUtil {

    /**
     * 数据源
     */
    public final static String DATA_SOURCE = "dataSource";

    /**
     * 灰度数据源后缀hd
     */
    public final static String GRAY_SUFFIX = "hd";

    /**
     * 获取head标识
     *
     * @param requestDataSource
     * @return  String
     */
    public static String getDataSource(String requestDataSource) {
        if (StringUtils.isNotBlank(requestDataSource)) {
            log.debug("初始化AOP-获取到的dataSource：{}", requestDataSource);
            return requestDataSource;
        }
        return DynamicSourceTtl.MASTER_DATASOURCE;
    }

    /**
     * 设置数据源
     *
     * @param castId 投保ID
     * @return void
     */
    public static void setDataSource(String castId) {
        String dataSource = DynamicSourceTtl.get();
        log.debug("根据投保ID设置数据源：【投保ID：{}，对应的原数据环境为：{}】", castId, dataSource);
        if (StringUtils.isNotBlank(castId) && StringUtils.contains(castId, GRAY_SUFFIX)) {
            dataSource =  DynamicSourceTtl.SLAVE_DATASOURCE;
        }
        if(StringUtils.isBlank(dataSource)) {
            dataSource = DynamicSourceTtl.MASTER_DATASOURCE;
        }
        //设置数据源：主要用来判断是否是灰度环境
        DynamicSourceTtl.push(dataSource);
        log.debug("根据投保ID设置数据源：【投保ID：{}，对应的新数据环境为：{}】，设置完毕！", castId, dataSource);
    }



    /**
     * 设置数据源
     *
     * @param dataSource
     */
    public static void put(String dataSource) {
        DynamicSourceTtl.push(dataSource);
    }

    /**
     * 清除数据源
     */
    public static void clear() {
        DynamicSourceTtl.clear();
    }

    /**
     * 获取数据源表示
     *
     * @return
     */
    public static String get() {
        return DynamicSourceTtl.get();
    }

}
