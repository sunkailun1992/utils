package com.kellen.utils.verify;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link Phone} 按 Hutool 的手机号规则校验（13–19 号段），使用注解自身的提示语，并支持容器元素。
 */
class PhoneValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsCurrentMobilePrefixes() {
        for (String phone : List.of("16612345678", "19912345678", "13812345678")) {
            assertThat(validator.validate(new Contact(phone))).as(phone).isEmpty();
        }
    }

    @Test
    void rejectsMalformedNumbers() {
        for (String phone : List.of("12345678901", "1381234567", "abc")) {
            assertThat(validator.validate(new Contact(phone))).as(phone).hasSize(1);
        }
    }

    @Test
    void usesAnnotationMessage() {
        Set<ConstraintViolation<CustomMessageContact>> violations = validator.validate(new CustomMessageContact("123"));

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly("请填写手机号");
    }

    @Test
    void blankIsValid() {
        assertThat(validator.validate(new Contact(null))).isEmpty();
        assertThat(validator.validate(new Contact(""))).isEmpty();
    }

    @Test
    void appliesToContainerElements() {
        assertThat(validator.validate(new Contacts(List.of("13812345678", "123")))).hasSize(1);
    }

    record Contact(@Phone String phone) {
    }

    record CustomMessageContact(@Phone(message = "请填写手机号") String phone) {
    }

    record Contacts(List<@Phone String> phones) {
    }
}
