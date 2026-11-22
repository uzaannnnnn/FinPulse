package com.finpulse.exception;

/**
 * Thrown when a domain business rule or query parameter validation fails (HTTP 400).
 */
public class BusinessValidationException extends RuntimeException {

    public BusinessValidationException(String message) {
        super(message);
    }
}
