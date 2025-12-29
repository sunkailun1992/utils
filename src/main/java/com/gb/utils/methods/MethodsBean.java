package com.gb.utils.methods;

import lombok.Data;

import java.util.Map;

/**
 * TODO 方法类
 *
 * @author 孙凯伦
 * @className MethodsBean
 * @email 376253703@qq.com
 * 
 * @time 2022/5/7 16:36
 */
@Data
public class MethodsBean {
    /**
     * 执行的类
     */
    private String classz;
    /**
     * 执行的方法
     */
    private String methodName;
    /**
     * 传入的参数
     */
    private Map<String, Object> paramMap;
}
