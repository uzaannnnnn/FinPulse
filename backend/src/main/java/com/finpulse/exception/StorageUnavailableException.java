package com.finpulse.exception;

/**
 * Thrown when Supabase Storage is unconfigured or temporarily unreachable (HTTP 503).
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }
}
