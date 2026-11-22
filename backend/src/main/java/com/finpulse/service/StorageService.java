package com.finpulse.service;

import com.finpulse.dto.ReceiptUploadResponse;
import com.finpulse.dto.ReportUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Abstraction for uploading transaction receipt images and generated reports to object storage.
 */
public interface StorageService {

    ReceiptUploadResponse uploadReceipt(UUID userId, MultipartFile file);

    ReportUploadResult uploadReport(UUID userId, String fileName, String contentType, byte[] bytes);
}
