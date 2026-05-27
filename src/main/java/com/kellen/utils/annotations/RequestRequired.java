package com.kellen.utils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * 请求校验
 * @author 孙凯伦
 * @DateTime 2019/5/6  11:30 AM
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestRequired {

}
