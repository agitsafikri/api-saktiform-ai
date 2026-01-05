package com.saktiform.api.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = UniqueProductNameValidator.class)
@Target({ ElementType.TYPE }) // validasi di level class (karena butuh id dan name)
@Retention(RetentionPolicy.RUNTIME)
public @interface  UniqueProductName {
    String message() default "Nama produk sudah digunakan";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}