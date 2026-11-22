package com.finpulse.messaging;

import com.finpulse.dto.ReportUploadResult;
import com.finpulse.entity.ReportExport;
import com.finpulse.entity.ReportFormat;
import com.finpulse.entity.ReportStatus;
import com.finpulse.entity.User;
import com.finpulse.exception.StorageUnavailableException;
import com.finpulse.repository.ReportExportRepository;
import com.finpulse.service.ReportGeneratorService;
import com.finpulse.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExportConsumerTest {

    @Mock
    private ReportExportRepository reportExportRepository;

    @Mock
    private ReportGeneratorService reportGeneratorService;

    @Mock
    private StorageService storageService;

    private ReportExportConsumer consumer;

    private UUID userId;
    private UUID reportId;
    private ReportExport pendingJob;

    @BeforeEach
    void setUp() {
        consumer = new ReportExportConsumer(reportExportRepository, reportGeneratorService, storageService);
        userId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        User user = new User("worker@finpulse.app", "hash", "Worker");
        user.setId(userId);
        pendingJob = new ReportExport(user, 11, 2026, ReportFormat.PDF, ReportStatus.PENDING);
        pendingJob.setId(reportId);
    }

    @Test
    void shouldTransitionPendingToProcessingToCompletedAndPersistDownloadMetadata() {
        ReportExportRequestedEvent event = new ReportExportRequestedEvent(
                reportId, userId, 11, 2026, ReportFormat.PDF
        );
        byte[] pdfBytes = "%PDF-1.4 test content".getBytes();
        ReportGeneratorService.GeneratedReport generated = new ReportGeneratorService.GeneratedReport(
                "finpulse-report-2026-11.pdf", "application/pdf", pdfBytes
        );
        ReportUploadResult uploadResult = new ReportUploadResult(
                "https://xyz.supabase.co/storage/v1/object/public/reports/" + userId + "/reports/finpulse-report-2026-11.pdf",
                userId + "/reports/finpulse-report-2026-11.pdf",
                "application/pdf",
                pdfBytes.length
        );

        when(reportExportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(pendingJob));
        when(reportExportRepository.save(any(ReportExport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportGeneratorService.generate(userId, 11, 2026, ReportFormat.PDF)).thenReturn(generated);
        when(storageService.uploadReport(userId, generated.fileName(), generated.contentType(), pdfBytes))
                .thenReturn(uploadResult);

        consumer.handleReportExportRequested(event);

        verify(reportExportRepository, times(2)).save(pendingJob);
        assertThat(pendingJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(pendingJob.getFileName()).isEqualTo("finpulse-report-2026-11.pdf");
        assertThat(pendingJob.getFilePath()).isEqualTo(uploadResult.filePath());
        assertThat(pendingJob.getFileUrl()).isEqualTo(uploadResult.fileUrl());
        assertThat(pendingJob.getContentType()).isEqualTo("application/pdf");
        assertThat(pendingJob.getSizeBytes()).isEqualTo(pdfBytes.length);
        assertThat(pendingJob.getErrorMessage()).isNull();
        assertThat(pendingJob.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldMarkJobFailedWithSafeMessageWhenStorageThrowsUnavailable() {
        ReportExportRequestedEvent event = new ReportExportRequestedEvent(
                reportId, userId, 11, 2026, ReportFormat.CSV
        );
        byte[] csvBytes = "col1,col2\r\n1,2".getBytes();
        ReportGeneratorService.GeneratedReport generated = new ReportGeneratorService.GeneratedReport(
                "finpulse-report-2026-11.csv", "text/csv; charset=UTF-8", csvBytes
        );

        when(reportExportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(pendingJob));
        when(reportExportRepository.save(any(ReportExport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportGeneratorService.generate(userId, 11, 2026, ReportFormat.CSV)).thenReturn(generated);
        when(storageService.uploadReport(userId, generated.fileName(), generated.contentType(), csvBytes))
                .thenThrow(new StorageUnavailableException("Layanan Supabase Storage belum dikonfigurasi pada server"));

        consumer.handleReportExportRequested(event);

        assertThat(pendingJob.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(pendingJob.getErrorMessage()).isEqualTo("Layanan Supabase Storage belum dikonfigurasi pada server");
        assertThat(pendingJob.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldSkipAlreadyCompletedJobIdempotently() {
        pendingJob.setStatus(ReportStatus.COMPLETED);
        ReportExportRequestedEvent event = new ReportExportRequestedEvent(
                reportId, userId, 11, 2026, ReportFormat.PDF
        );
        when(reportExportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(pendingJob));

        consumer.handleReportExportRequested(event);

        verify(reportGeneratorService, never()).generate(any(), any(Integer.class), any(Integer.class), any());
        verify(storageService, never()).uploadReport(any(), any(), any(), any());
        verify(reportExportRepository, never()).save(any());
    }
}
