package com.finpulse.controller;

import com.finpulse.dto.DashboardSummaryDto;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for dashboard financial KPIs, monthly cashflow, category breakdown, and budget progress.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Ringkasan KPI (di-cache Redis), grafik arus kas bulanan, dan progres anggaran")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    public static final String CACHE_STATUS_HEADER = "X-Cache-Status";

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(
            operationId = "getDashboardSummary",
            summary = "Ambil ringkasan KPI saldo, grafik arus kas bulanan, progres anggaran, dan transaksi terbaru (didukung Redis Cache)"
    )
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "monthsBack", defaultValue = "6") int monthsBack
    ) {
        DashboardSummaryDto summary = dashboardService.getSummary(
                principal.getId(), month, year, monthsBack
        );
        String cacheStatus = summary.fromCache() ? "HIT" : "MISS";
        return ResponseEntity.ok()
                .header(CACHE_STATUS_HEADER, cacheStatus)
                .body(summary);
    }
}
