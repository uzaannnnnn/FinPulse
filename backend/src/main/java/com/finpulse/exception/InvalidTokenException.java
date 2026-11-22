package com.finpulse.exception;

/**
 * Thrown when an access or refresh token is missing, malformed, revoked, or expired.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
