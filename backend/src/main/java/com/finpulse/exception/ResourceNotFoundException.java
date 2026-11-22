package com.finpulse.exception;

/**
 * Thrown when a requested domain resource cannot be found for the authenticated user.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
