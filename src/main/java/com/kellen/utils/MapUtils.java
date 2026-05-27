package com.kellen.utils;

import cn.hutool.core.map.MapUtil;

import java.util.Map;
import java.util.TreeMap;

/**
 * Map工具类
 *
 * @author 孙凯伦
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
     * @return com.kellen.utils.ApiResponse
     * @author 孙凯伦
     * @since 2021-02-09
     */
    public static TreeMap<String, Object> getTreeMap (Map<String, Object> map){
        TreeMap<String, Object> treeMap = new TreeMap<>();
        if (MapUtil.isEmpty(map)) {
            return treeMap;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if(null == value){
                continue;
            }
            if(value instanceof Map){
                Map<String, Object> bodyMap = (Map<String, Object>) value;
                TreeMap<String, Object> bodyTree = new TreeMap<>();
                for (String key : bodyMap.keySet()) {
                    Object bodyValue = bodyMap.get(key);
                    if(null == bodyValue){
                        continue;
                    }
                    bodyTree.put(key, bodyValue);
                }
                if(MapUtil.isNotEmpty(bodyTree)){
                    value = bodyTree;
                }
            }
            treeMap.put(entry.getKey(),value);
        }
        return treeMap;
    }
}
