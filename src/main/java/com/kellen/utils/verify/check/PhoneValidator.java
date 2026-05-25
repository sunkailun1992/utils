package com.kellen.utils.verify.check;

import com.kellen.utils.verify.Phone;
import org.apache.commons.lang3.StringUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * @ClassName PhoneValidator
 * @Description 手机号校验
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/7/27 10:35 上午
 */
public class PhoneValidator implements ConstraintValidator<Phone, String> {

    /**
     * 校验
     * @param phone
     * @param constraintValidatorContext
     * @return
     */
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext constraintValidatorContext) {

        if (!StringUtils.isEmpty(phone)) {
            // 禁用默认提示信息
            constraintValidatorContext.disableDefaultConstraintViolation();
            // 设置提示语
            constraintValidatorContext.buildConstraintViolationWithTemplate("手机号格式错误").addConstraintViolation();

            String regex = "^1(3|4|5|7|8)\\d{9}$";
            return phone.matches(regex);
        }
        return true;
    }

}
