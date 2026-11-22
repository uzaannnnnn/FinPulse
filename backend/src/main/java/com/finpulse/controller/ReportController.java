package com.finpulse.controller;

import com.finpulse.dto.CreateReportExportRequest;
import com.finpulse.dto.ReportExportDto;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for submitting and polling asynchronous monthly report export jobs.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Permintaan dan polling status ekspor laporan bulanan PDF/CSV asinkron")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportExportService reportExportService;

    public ReportController(ReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    @PostMapping("/export")
    @Operation(
            operationId = "requestReportExport",
            summary = "Ajukan background job ekspor laporan bulanan (PDF atau CSV) melalui RabbitMQ"
    )
    public ResponseEntity<ReportExportDto> requestReportExport(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @Valid @RequestBody CreateReportExportRequest request
    ) {
        ReportExportDto response = reportExportService.requestExport(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(
            operationId = "listReportExports",
            summary = "Ambil daftar riwayat permintaan ekspor laporan milik pengguna"
    )
    public ResponseEntity<List<ReportExportDto>> listReportExports(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(reportExportService.listExports(principal.getId(), limit));
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getReportExportById",
            summary = "Ambil status dan tautan unduhan satu job ekspor laporan milik pengguna"
    )
    public ResponseEntity<ReportExportDto> getReportExportById(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(reportExportService.getExportById(principal.getId(), id));
    }
}
