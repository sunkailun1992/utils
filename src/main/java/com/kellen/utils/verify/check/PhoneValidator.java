package com.kellen.utils.verify.check;

import cn.hutool.core.util.PhoneUtil;
import com.kellen.utils.verify.Phone;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link Phone} 的校验器：空值通过，其余交给 Hutool {@link PhoneUtil#isMobile(CharSequence)}；
 * 提示语使用注解自身的 {@code message}。
 *
 * @author 孙凯伦
 */
public class PhoneValidator implements ConstraintValidator<Phone, String> {

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        return phone == null || phone.isEmpty() || PhoneUtil.isMobile(phone);
    }
}
