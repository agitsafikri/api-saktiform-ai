package com.saktiform.api.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = UniqueProductUrlValidator.class)
@Target({ ElementType.TYPE }) // validasi di level class (karena butuh id dan name)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueProductUrl {
    String message() default "Url sudah digunakan";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
