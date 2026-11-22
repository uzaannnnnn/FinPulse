package com.finpulse.dto;

/**
 * Result metadata returned after uploading a generated report file to object storage.
 */
public record ReportUploadResult(
        String fileUrl,
        String filePath,
        String contentType,
        long sizeBytes
) {
}
