package com.finpulse.exception;

/**
 * Thrown when a user attempts to create a duplicate budget for the same category and month/year (HTTP 409).
 */
public class DuplicateBudgetException extends RuntimeException {

    public DuplicateBudgetException(String message) {
        super(message);
    }
}
