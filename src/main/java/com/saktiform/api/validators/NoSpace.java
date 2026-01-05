package com.saktiform.api.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NoSpaceValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface NoSpace {
    String message() default "Field tidak boleh mengandung spasi";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

