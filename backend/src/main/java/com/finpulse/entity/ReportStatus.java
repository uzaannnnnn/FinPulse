package com.finpulse.entity;

/**
 * Lifecycle state of an asynchronous monthly report export job.
 */
public enum ReportStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
