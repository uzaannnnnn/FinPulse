package com.finpulse.dto;

/**
 * Response payload returned by {@code POST /api/transactions/receipt}.
 */
public record ReceiptUploadResponse(
        String receiptUrl,
        String filePath,
        String contentType,
        long sizeBytes
) {
}
