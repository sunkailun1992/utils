package com.gb.utils;

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.TreeMap;

/**
 * Map工具类
 *
 * @author sunkailun
 * @DateTime 2020/12/29  上午11:14
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class MapUtils {

    private MapUtils(){

    }

    /**
     * Map转TreeMap
     * @param map
     * @return com.utils.Json
     * @author sunx
     * @since 2021-02-09
     */
    public static TreeMap<String, Object> getTreeMap (Map<String, Object> map){
        TreeMap<String, Object> treeMap = Maps.newTreeMap();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if(null == value){
                continue;
            }
            if(value instanceof Map){
                Map<String, Object> bodyMap = (Map<String, Object>) value;
                TreeMap<String, Object> bodyTree = Maps.newTreeMap();
                for (String key : bodyMap.keySet()) {
                    Object bodyValue = bodyMap.get(key);
                    if(null == bodyValue){
                        continue;
                    }
                    bodyTree.put(key, bodyValue);
                }
                if(org.apache.commons.collections.MapUtils.isNotEmpty(bodyTree)){
                    value = bodyTree;
                }
            }
            treeMap.put(entry.getKey(),value);
        }
        return treeMap;
    }
}
