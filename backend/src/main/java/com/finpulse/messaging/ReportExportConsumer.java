package com.finpulse.messaging;

import com.finpulse.dto.ReportUploadResult;
import com.finpulse.entity.ReportExport;
import com.finpulse.entity.ReportStatus;
import com.finpulse.exception.StorageUnavailableException;
import com.finpulse.repository.ReportExportRepository;
import com.finpulse.service.ReportGeneratorService;
import com.finpulse.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * RabbitMQ worker processing asynchronous monthly PDF/CSV report export jobs.
 */
@Component
public class ReportExportConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportExportConsumer.class);

    private final ReportExportRepository reportExportRepository;
    private final ReportGeneratorService reportGeneratorService;
    private final StorageService storageService;

    public ReportExportConsumer(
            ReportExportRepository reportExportRepository,
            ReportGeneratorService reportGeneratorService,
            StorageService storageService
    ) {
        this.reportExportRepository = reportExportRepository;
        this.reportGeneratorService = reportGeneratorService;
        this.storageService = storageService;
    }

    @RabbitListener(queues = RabbitMqConfig.REPORT_EXPORT_QUEUE)
    public void handleReportExportRequested(ReportExportRequestedEvent event) {
        if (event == null || event.reportId() == null || event.userId() == null) {
            return;
        }

        Optional<ReportExport> reportOpt = reportExportRepository.findByIdAndUserId(
                event.reportId(), event.userId()
        );
        if (reportOpt.isEmpty()) {
            log.warn("ReportExport job not found for reportId={} userId={}; ignoring message",
                    event.reportId(), event.userId());
            return;
        }

        ReportExport report = reportOpt.get();
        if (report.getStatus() == ReportStatus.COMPLETED) {
            log.info("ReportExport job id={} already COMPLETED; skipping duplicate delivery", report.getId());
            return;
        }

        report.setStatus(ReportStatus.PROCESSING);
        report.setErrorMessage(null);
        report = reportExportRepository.save(report);

        try {
            ReportGeneratorService.GeneratedReport generated = reportGeneratorService.generate(
                    event.userId(),
                    event.month(),
                    event.year(),
                    event.format()
            );

            ReportUploadResult uploadResult = storageService.uploadReport(
                    event.userId(),
                    generated.fileName(),
                    generated.contentType(),
                    generated.bytes()
            );

            report.setStatus(ReportStatus.COMPLETED);
            report.setFileName(generated.fileName());
            report.setFilePath(uploadResult.filePath());
            report.setFileUrl(uploadResult.fileUrl());
            report.setContentType(uploadResult.contentType());
            report.setSizeBytes(uploadResult.sizeBytes());
            report.setErrorMessage(null);
            report.setCompletedAt(Instant.now());
            reportExportRepository.save(report);

            log.info("Completed report export id={} for userId={} format={} sizeBytes={}",
                    report.getId(), event.userId(), event.format(), uploadResult.sizeBytes());
        } catch (Exception ex) {
            log.error("Report export failed for reportId={} userId={}", report.getId(), event.userId(), ex);
            String safeMessage = (ex instanceof StorageUnavailableException && ex.getMessage() != null && !ex.getMessage().isBlank())
                    ? ex.getMessage()
                    : "Gagal memproses ekspor laporan; silakan coba beberapa saat lagi";

            report.setStatus(ReportStatus.FAILED);
            report.setErrorMessage(safeMessage);
            report.setCompletedAt(Instant.now());
            reportExportRepository.save(report);
        }
    }
}
