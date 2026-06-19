package com.kellen.utils.http;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 请求工具类
 * 
 */
public class RequestUtil {
    /**
     * 获取request中的参数集合转对象
     * 用法：User user = (User) RequestUtil.getParameterObject(request, new User())
     *
     * @param request obj
     * @return
     */
    public static Object getParameterObject(HttpServletRequest request, Object obj) {
        return JakartaServletUtil.fillBean(request, obj, true);
    }

    /**
     * 获取request中的参数集合转Map
     * Map<String,String> parameterMap = RequestUtil.getParameterMap(request)
     *
     * @param request
     * @return
     */
    public static Map<String, String> getParameterMap(HttpServletRequest request) {
        Map<String, String> parameterMap = JakartaServletUtil.getParamMap(request);
        if (MapUtil.isEmpty(parameterMap)) {
            return parameterMap;
        }
        return parameterMap.entrySet().stream()
                .filter(entry -> StrUtil.isNotEmpty(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
