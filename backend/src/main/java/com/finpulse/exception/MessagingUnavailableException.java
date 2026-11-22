package com.finpulse.exception;

/**
 * Thrown when the RabbitMQ message broker is unavailable while enqueueing required asynchronous jobs.
 */
public class MessagingUnavailableException extends RuntimeException {

    public MessagingUnavailableException(String message) {
        super(message);
    }

    public MessagingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
