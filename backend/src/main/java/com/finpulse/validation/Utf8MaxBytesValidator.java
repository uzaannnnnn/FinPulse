package com.finpulse.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Constraint validator for {@link Utf8MaxBytes}.
 */
public class Utf8MaxBytesValidator implements ConstraintValidator<Utf8MaxBytes, String> {

    private int max;

    @Override
    public void initialize(Utf8MaxBytes constraintAnnotation) {
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
