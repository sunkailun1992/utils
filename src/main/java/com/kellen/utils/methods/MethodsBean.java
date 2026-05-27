package com.kellen.utils.methods;

import lombok.Data;

import java.util.Map;

/**
 * Methods 注解扩展执行参数。
 *
 * @author 孙凯伦
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
