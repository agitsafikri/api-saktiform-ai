package com.saktiform.api.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoSpaceValidator implements ConstraintValidator<NoSpace, String> {

    @Override
    public void initialize(NoSpace constraintAnnotation) {
        // bisa diabaikan kalau tidak ada inisialisasi khusus
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // biar @NotNull yang handle null
        return !value.contains(" ");
    }
}
