package com.finpulse.exception;

/**
 * Thrown when attempting to register with an email address that already exists.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
