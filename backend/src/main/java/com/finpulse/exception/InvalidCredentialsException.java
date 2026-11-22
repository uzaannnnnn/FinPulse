package com.finpulse.exception;

/**
 * Thrown when user login fails due to invalid email or password.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
