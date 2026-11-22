package com.finpulse.dto;

import com.finpulse.entity.ReportExport;
import com.finpulse.entity.ReportFormat;
import com.finpulse.entity.ReportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Asynchronous report export job metadata returned by {@code /api/reports}.
 */
public record ReportExportDto(
        UUID id,
        int month,
        int year,
        ReportFormat format,
        ReportStatus status,
        String fileName,
        String fileUrl,
        String contentType,
        Long sizeBytes,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {

    public static ReportExportDto from(ReportExport report) {
        return new ReportExportDto(
                report.getId(),
                report.getMonth(),
                report.getYear(),
                report.getFormat(),
                report.getStatus(),
                report.getFileName(),
                report.getFileUrl(),
                report.getContentType(),
                report.getSizeBytes(),
                report.getErrorMessage(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                report.getCompletedAt()
        );
    }
}
