package com.finpulse.service;

import com.finpulse.config.SupabaseProperties;
import com.finpulse.dto.ReceiptUploadResponse;
import com.finpulse.dto.ReportUploadResult;
import com.finpulse.exception.InvalidFileException;
import com.finpulse.exception.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Supabase Storage REST API implementation for validating and uploading transaction receipt images.
 */
@Service
public class SupabaseStorageService implements StorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);
    private static final Pattern SAFE_BUCKET_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final SupabaseProperties supabaseProperties;
    private final RestClient restClient;

    public SupabaseStorageService(
            SupabaseProperties supabaseProperties,
            @Qualifier("supabaseRestClient") RestClient restClient
    ) {
        this.supabaseProperties = supabaseProperties;
        this.restClient = restClient;
    }

    @Override
    public ReceiptUploadResponse uploadReceipt(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File struk tidak boleh kosong");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("Ukuran file melebihi batas maksimal 5 MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidFileException("Gagal membaca file struk yang diunggah");
        }

        if (bytes.length == 0) {
            throw new InvalidFileException("File struk tidak boleh kosong");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("Ukuran file melebihi batas maksimal 5 MB");
        }

        String declaredContentType = normalizeContentType(file.getContentType());
        AllowedImageType detectedType = detectImageType(bytes);
        if (detectedType == null) {
            throw new InvalidFileException(
                    "Format file tidak valid; hanya gambar JPEG, PNG, atau WebP yang diizinkan"
            );
        }
        if (declaredContentType == null || !detectedType.contentType.equals(declaredContentType)) {
            throw new InvalidFileException(
                    "Tipe MIME file tidak sesuai dengan isi gambar (hanya image/jpeg, image/png, image/webp)"
            );
        }

        if (!supabaseProperties.isConfigured()) {
            throw new StorageUnavailableException(
                    "Layanan Supabase Storage belum dikonfigurasi pada server"
            );
        }

        String objectPath = userId + "/" + UUID.randomUUID() + "." + detectedType.extension;
        String publicUrl = uploadObjectToSupabase(
                userId,
                supabaseProperties.getStorageBucket(),
                objectPath,
                detectedType.contentType,
                bytes,
                "receipt"
        );

        return new ReceiptUploadResponse(publicUrl, objectPath, detectedType.contentType, bytes.length);
    }

    @Override
    public ReportUploadResult uploadReport(UUID userId, String fileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidFileException("File laporan tidak boleh kosong");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidFileException("Nama file laporan wajib diisi");
        }
        if (!supabaseProperties.isReportsConfigured()) {
            throw new StorageUnavailableException(
                    "Layanan Supabase Storage belum dikonfigurasi pada server"
            );
        }

        String sanitizedFileName = sanitizeReportFileName(fileName);
        String resolvedContentType = (contentType == null || contentType.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType.trim();

        String objectPath = userId + "/reports/" + UUID.randomUUID() + "-" + sanitizedFileName;
        String publicUrl = uploadObjectToSupabase(
                userId,
                supabaseProperties.getReportsBucket(),
                objectPath,
                resolvedContentType,
                bytes,
                "report"
        );

        return new ReportUploadResult(publicUrl, objectPath, resolvedContentType, bytes.length);
    }

    private String uploadObjectToSupabase(
            UUID userId,
            String rawBucket,
            String objectPath,
            String contentType,
            byte[] bytes,
            String assetLabel
    ) {
        String baseUrl = validateBaseUrl(supabaseProperties.getNormalizedUrl());
        String bucket = validateBucketName(normalizeBucketName(rawBucket));
        String serviceKey = supabaseProperties.getServiceKey().trim();
        String uploadUrl = baseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;
        String publicUrl = baseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;

        try {
            restClient.post()
                    .uri(uploadUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                    .header("apikey", serviceKey)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.error(
                    "Supabase Storage rejected {} upload for userId={} with upstream status={}",
                    assetLabel,
                    userId,
                    ex.getStatusCode().value()
            );
            throw new StorageUnavailableException(
                    "Layanan Supabase Storage sedang tidak tersedia; silakan coba beberapa saat lagi"
            );
        } catch (RestClientException ex) {
            log.error("Failed uploading {} to Supabase Storage for userId={}", assetLabel, userId, ex);
            throw new StorageUnavailableException(
                    "Layanan Supabase Storage sedang tidak tersedia; silakan coba beberapa saat lagi"
            );
        }

        return publicUrl;
    }

    private String sanitizeReportFileName(String fileName) {
        String cleaned = fileName.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_");
        }
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(cleaned.length() - 120);
        }
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("_")) {
            return "finpulse-report.bin";
        }
        return cleaned;
    }

    private String validateBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new StorageUnavailableException("Konfigurasi URL Supabase Storage tidak valid");
        }
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || host == null
                    || host.isBlank()
                    || uri.getUserInfo() != null) {
                throw new StorageUnavailableException("Konfigurasi URL Supabase Storage tidak valid");
            }
            return baseUrl;
        } catch (IllegalArgumentException ex) {
            throw new StorageUnavailableException("Konfigurasi URL Supabase Storage tidak valid");
        }
    }

    private String validateBucketName(String bucket) {
        if (bucket == null
                || bucket.isBlank()
                || bucket.contains("..")
                || !SAFE_BUCKET_NAME.matcher(bucket).matches()) {
            throw new StorageUnavailableException("Konfigurasi bucket Supabase Storage tidak valid");
        }
        return bucket;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int semicolonIdx = contentType.indexOf(';');
        String baseType = semicolonIdx >= 0 ? contentType.substring(0, semicolonIdx) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBucketName(String bucket) {
        String trimmed = bucket.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private AllowedImageType detectImageType(byte[] bytes) {
        if (isJpeg(bytes)) {
            return AllowedImageType.JPEG;
        }
        if (isPng(bytes)) {
            return AllowedImageType.PNG;
        }
        if (isWebp(bytes)) {
            return AllowedImageType.WEBP;
        }
        return null;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private enum AllowedImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        AllowedImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }
}
