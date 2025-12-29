package com.gb.utils;

import org.apache.commons.beanutils.BeanUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName RequestUtil
 * @Description 请求工具类
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/7/22 10:10 上午
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
        Map map = request.getParameterMap();
        try {
            BeanUtils.populate(obj, map);
            return obj;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取request中的参数集合转Map
     * Map<String,String> parameterMap = RequestUtil.getParameterMap(request)
     *
     * @param request
     * @return
     */
    public static Map<String, String> getParameterMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);
            if (paramValues.length == 1) {
                String paramValue = paramValues[0];
                if (paramValue.length() != 0) {
                    map.put(paramName, paramValue);
                }
            }
        }
        return map;
    }
}
