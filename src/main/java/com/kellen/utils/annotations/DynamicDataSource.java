package com.kellen.utils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author 孙凯伦
 * @Date 2021/8/6 10:38
 * @Classname DynamicDataSource
 * @Description 获取数据源标识
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamicDataSource {
}
