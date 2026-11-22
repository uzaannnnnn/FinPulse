package com.finpulse.exception;

/**
 * Thrown when an uploaded receipt file fails size, MIME type, or magic-byte validation (HTTP 400).
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
