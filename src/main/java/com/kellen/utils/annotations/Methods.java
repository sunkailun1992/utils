package com.kellen.utils.annotations;

import java.lang.annotation.*;

/**
 * 自定义注解获取方法名称和作用
 *
 * @ClassName: Verify
 * @author: 孙凯伦
 * @date: 2016年10月10日 上午10:25:56
 * @Description: TODO
 * @email: 376253703@qq.com
 * @version: V1.0
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME) // 注解会在class字节码文件中存在，在运行时可以通过反射获取到
@Target({ElementType.METHOD})//定义注解的作用目标**作用范围字段、枚举的常量/方法
@Documented//说明该注解将被包含在javadoc中
public @interface Methods {
    /**
     * 方法名称
     *
     * @return
     */
    String methodsName() default "";

    /**
     * 方法
     *
     * @return
     */
    String methods() default "";

    /**
     * 说明
     *
     * @return
     */
    String description() default "";

    /**
     * 执行之前
     * @return
     */
    String performBefore() default "";

    /**
     * 执行之后
     * @return
     */
    String performAfter() default "";
}
