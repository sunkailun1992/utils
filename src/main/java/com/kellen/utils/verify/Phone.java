package com.kellen.utils.verify;

import com.kellen.utils.verify.check.PhoneValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 手机号校验注解：按 Hutool {@code PhoneUtil.isMobile} 的规则校验中国大陆手机号（13–19 号段）。
 *
 * <p>{@code null} 与空串视为通过，必填请另加 {@code @NotBlank}；可标在字段、方法、参数与容器元素上，
 * 例如 {@code List<@Phone String>}。</p>
 *
 * @author 孙凯伦
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
public @interface Phone {

    /**
     * 校验失败提示语。
     *
     * @return 提示语
     */
    String message() default "手机号格式错误";

    /**
     * 校验分组。
     *
     * @return 分组
     */
    Class<?>[] groups() default {};

    /**
     * 附加负载。
     *
     * @return 负载
     */
    Class<? extends Payload>[] payload() default {};
}
