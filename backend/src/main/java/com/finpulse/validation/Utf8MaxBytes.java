package com.finpulse.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string's UTF-8 encoded byte length does not exceed {@link #max()}.
 */
@Documented
@Constraint(validatedBy = Utf8MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8MaxBytes {

    int max();

    String message() default "Panjang teks melebihi batas byte UTF-8 maksimum";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
