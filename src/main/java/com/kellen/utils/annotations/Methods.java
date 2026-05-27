package com.kellen.utils.annotations;

import java.lang.annotation.*;

/**
 * 自定义注解获取方法名称和作用
 *
 * @author 孙凯伦
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME) // 注解保留到运行期，AOP 日志逻辑可通过反射读取。
@Target({ElementType.METHOD}) // 注解只允许标记方法。
@Documented // 注解会出现在生成的 JavaDoc 中。
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
