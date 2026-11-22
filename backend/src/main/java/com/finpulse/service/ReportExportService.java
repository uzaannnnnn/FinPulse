package com.finpulse.service;

import com.finpulse.dto.CreateReportExportRequest;
import com.finpulse.dto.ReportExportDto;
import com.finpulse.entity.ReportExport;
import com.finpulse.entity.ReportStatus;
import com.finpulse.entity.User;
import com.finpulse.exception.BusinessValidationException;
import com.finpulse.exception.MessagingUnavailableException;
import com.finpulse.exception.ResourceNotFoundException;
import com.finpulse.messaging.EventPublisher;
import com.finpulse.messaging.ReportExportRequestedEvent;
import com.finpulse.repository.ReportExportRepository;
import com.finpulse.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User-isolated service for creating and polling asynchronous monthly report export jobs.
 */
@Service
public class ReportExportService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final ReportExportRepository reportExportRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;

    public ReportExportService(
            ReportExportRepository reportExportRepository,
            UserRepository userRepository,
            EventPublisher eventPublisher
    ) {
        this.reportExportRepository = reportExportRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public ReportExportDto requestExport(UUID userId, CreateReportExportRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan"));

        ReportExport export = new ReportExport(
                user,
                request.month(),
                request.year(),
                request.format(),
                ReportStatus.PENDING
        );
        // Persisted and committed before enqueueing so RabbitMQ workers always observe the job row.
        ReportExport saved = reportExportRepository.saveAndFlush(export);

        try {
            eventPublisher.publishReportExportRequested(new ReportExportRequestedEvent(
                    saved.getId(),
                    userId,
                    saved.getMonth(),
                    saved.getYear(),
                    saved.getFormat()
            ));
        } catch (MessagingUnavailableException ex) {
            saved.setStatus(ReportStatus.FAILED);
            saved.setErrorMessage(ex.getMessage());
            saved.setCompletedAt(Instant.now());
            reportExportRepository.saveAndFlush(saved);
            throw ex;
        }

        return ReportExportDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportExportDto> listExports(UUID userId, int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessValidationException("Parameter 'limit' harus antara 1 sampai 100");
        }
        return reportExportRepository.findRecentByUserId(userId, PageRequest.of(0, limit))
                .stream()
                .map(ReportExportDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportExportDto getExportById(UUID userId, UUID reportId) {
        ReportExport report = reportExportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job laporan tidak ditemukan"));
        return ReportExportDto.from(report);
    }
}
