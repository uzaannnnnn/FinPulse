package com.finpulse.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates the length of a non-blank string after trimming leading and trailing whitespace.
 */
@Documented
@Constraint(validatedBy = TrimmedSizeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TrimmedSize {

    int min() default 0;

    int max() default Integer.MAX_VALUE;

    String message() default "Panjang teks setelah dipangkas tidak valid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
