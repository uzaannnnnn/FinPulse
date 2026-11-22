package com.finpulse.messaging;

import com.finpulse.entity.ReportFormat;

import java.util.UUID;

/**
 * Event emitted when a user requests an asynchronous monthly PDF/CSV report export.
 */
public record ReportExportRequestedEvent(
        UUID reportId,
        UUID userId,
        int month,
        int year,
        ReportFormat format
) {
}
