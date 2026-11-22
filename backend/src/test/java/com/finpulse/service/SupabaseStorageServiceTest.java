package com.finpulse.service;

import com.finpulse.config.SupabaseProperties;
import com.finpulse.dto.ReceiptUploadResponse;
import com.finpulse.dto.ReportUploadResult;
import com.finpulse.exception.InvalidFileException;
import com.finpulse.exception.StorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseStorageServiceTest {

    private SupabaseProperties properties;
    private MockRestServiceServer mockServer;
    private SupabaseStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new SupabaseProperties();
        properties.setUrl("https://demo.supabase.co/");
        properties.setServiceKey("test-service-role-key");
        properties.setStorageBucket("receipts");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        storageService = new SupabaseStorageService(properties, builder.build());
    }

    @Test
    void uploadReceiptWithValidJpegShouldCallSupabaseAndReturnPublicUrl() {
        UUID userId = UUID.randomUUID();
        byte[] jpegBytes = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'
        };
        MockMultipartFile file = new MockMultipartFile("file", "struk.jpg", "image/jpeg", jpegBytes);

        mockServer.expect(requestTo(matchesPattern(
                        "^https://demo\\.supabase\\.co/storage/v1/object/receipts/" + userId + "/[0-9a-f\\-]+\\.jpg$")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-service-role-key"))
                .andExpect(header("apikey", "test-service-role-key"))
                .andExpect(header("x-upsert", "true"))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(jpegBytes))
                .andRespond(withSuccess("{\"Key\":\"receipts/ok.jpg\"}", MediaType.APPLICATION_JSON));

        ReceiptUploadResponse response = storageService.uploadReceipt(userId, file);

        mockServer.verify();
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.sizeBytes()).isEqualTo(jpegBytes.length);
        assertThat(response.filePath()).startsWith(userId + "/").endsWith(".jpg");
        assertThat(response.receiptUrl())
                .isEqualTo("https://demo.supabase.co/storage/v1/object/public/receipts/" + response.filePath());
    }

    @Test
    void uploadReceiptWithValidPngShouldUploadWithPngExtension() {
        UUID userId = UUID.randomUUID();
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "struk.png", "image/png", pngBytes);

        mockServer.expect(requestTo(matchesPattern(
                        "^https://demo\\.supabase\\.co/storage/v1/object/receipts/" + userId + "/[0-9a-f\\-]+\\.png$")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andRespond(withSuccess("{\"Key\":\"receipts/ok.png\"}", MediaType.APPLICATION_JSON));

        ReceiptUploadResponse response = storageService.uploadReceipt(userId, file);

        mockServer.verify();
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.filePath()).endsWith(".png");
        assertThat(response.sizeBytes()).isEqualTo(pngBytes.length);
    }

    @Test
    void uploadReceiptWithValidWebpShouldUploadWithWebpExtension() {
        UUID userId = UUID.randomUUID();
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };
        MockMultipartFile file = new MockMultipartFile("file", "struk.webp", "image/webp", webpBytes);

        mockServer.expect(requestTo(matchesPattern(
                        "^https://demo\\.supabase\\.co/storage/v1/object/receipts/" + userId + "/[0-9a-f\\-]+\\.webp$")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.parseMediaType("image/webp")))
                .andRespond(withSuccess("{\"Key\":\"receipts/ok.webp\"}", MediaType.APPLICATION_JSON));

        ReceiptUploadResponse response = storageService.uploadReceipt(userId, file);

        mockServer.verify();
        assertThat(response.contentType()).isEqualTo("image/webp");
        assertThat(response.filePath()).endsWith(".webp");
        assertThat(response.sizeBytes()).isEqualTo(webpBytes.length);
    }

    @Test
    void uploadReceiptShouldRejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storageService.uploadReceipt(UUID.randomUUID(), empty))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadReceiptShouldRejectOversizedFile() {
        byte[] oversized = new byte[(int) SupabaseStorageService.MAX_FILE_SIZE_BYTES + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "huge.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> storageService.uploadReceipt(UUID.randomUUID(), file))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadReceiptShouldRejectSpoofedExtensionAndMismatchedMimeType() {
        UUID userId = UUID.randomUUID();

        // Plain text disguised as JPEG
        MockMultipartFile spoofedJpeg = new MockMultipartFile(
                "file",
                "malicious.jpg",
                "image/jpeg",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)
        );
        assertThatThrownBy(() -> storageService.uploadReceipt(userId, spoofedJpeg))
                .isInstanceOf(InvalidFileException.class);

        // PNG magic bytes with mismatched MIME header image/jpeg
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile mismatchedMime = new MockMultipartFile("file", "fake.jpg", "image/jpeg", pngBytes);
        assertThatThrownBy(() -> storageService.uploadReceipt(userId, mismatchedMime))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadReceiptShouldThrowStorageUnavailableWhenNotConfigured() {
        properties.setUrl("");
        properties.setServiceKey("");

        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile("file", "struk.jpg", "image/jpeg", jpegBytes);

        assertThatThrownBy(() -> storageService.uploadReceipt(UUID.randomUUID(), file))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void uploadReceiptShouldThrowStorageUnavailableWhenSupabaseReturnsError() {
        UUID userId = UUID.randomUUID();
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile("file", "struk.jpg", "image/jpeg", jpegBytes);

        mockServer.expect(requestTo(matchesPattern("^https://demo\\.supabase\\.co/storage/v1/object/receipts/.*$")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> storageService.uploadReceipt(userId, file))
                .isInstanceOf(StorageUnavailableException.class);
        mockServer.verify();
    }

    @Test
    void uploadReceiptShouldIgnorePathTraversalFilenameAndUseIsolatedUuidPath() {
        UUID userId = UUID.randomUUID();
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile traversalFile = new MockMultipartFile(
                "file",
                "../../other-user/secret.jpg",
                "image/jpeg; charset=binary",
                jpegBytes
        );

        mockServer.expect(requestTo(matchesPattern(
                        "^https://demo\\.supabase\\.co/storage/v1/object/receipts/" + userId + "/[0-9a-f\\-]+\\.jpg$")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"Key\":\"ok\"}", MediaType.APPLICATION_JSON));

        ReceiptUploadResponse response = storageService.uploadReceipt(userId, traversalFile);

        mockServer.verify();
        assertThat(response.filePath()).startsWith(userId + "/").doesNotContain("..").doesNotContain("secret");
    }

    @Test
    void uploadReceiptShouldNotLeakSecretsOrUpstreamBodyOn4xxError() {
        UUID userId = UUID.randomUUID();
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile("file", "struk.jpg", "image/jpeg", jpegBytes);

        mockServer.expect(requestTo(matchesPattern("^https://demo\\.supabase\\.co/storage/v1/object/receipts/.*$")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"error\":\"invalid test-service-role-key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> storageService.uploadReceipt(userId, file))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageNotContaining("test-service-role-key")
                .hasMessageNotContaining("invalid");
        mockServer.verify();
    }

    @Test
    void uploadReceiptShouldThrowStorageUnavailableOnMalformedConfigUrlOrUnsafeBucket() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile("file", "struk.jpg", "image/jpeg", jpegBytes);

        properties.setUrl("javascript:alert(1)");
        assertThatThrownBy(() -> storageService.uploadReceipt(UUID.randomUUID(), file))
                .isInstanceOf(StorageUnavailableException.class);

        properties.setUrl("https://demo.supabase.co");
        properties.setStorageBucket("../private-bucket");
        assertThatThrownBy(() -> storageService.uploadReceipt(UUID.randomUUID(), file))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void uploadReportShouldIsolateUserReportsPathSanitizeTraversalAndHideSecretsOnError() {
        UUID userId = UUID.randomUUID();
        properties.setReportsBucket("reports");
        byte[] pdfBytes = "%PDF-1.4 report".getBytes(StandardCharsets.UTF_8);

        mockServer.expect(requestTo(matchesPattern(
                        "^https://demo\\.supabase\\.co/storage/v1/object/reports/" + userId + "/reports/[0-9a-f\\-]+-.*\\.pdf$")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-service-role-key"))
                .andExpect(header("apikey", "test-service-role-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andRespond(withSuccess("{\"Key\":\"reports/ok.pdf\"}", MediaType.APPLICATION_JSON));

        ReportUploadResult result = storageService.uploadReport(
                userId, "../../evil/finpulse-report-2026-11.pdf", "application/pdf", pdfBytes
        );

        mockServer.verify();
        assertThat(result.filePath()).startsWith(userId + "/reports/").doesNotContain("..");
        assertThat(result.fileUrl())
                .isEqualTo("https://demo.supabase.co/storage/v1/object/public/reports/" + result.filePath());
        assertThat(result.sizeBytes()).isEqualTo(pdfBytes.length);
    }
}
