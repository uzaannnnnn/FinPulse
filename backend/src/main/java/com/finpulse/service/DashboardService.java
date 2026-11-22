package com.finpulse.service;

import com.finpulse.dto.DashboardSummaryDto;
import com.finpulse.exception.BusinessValidationException;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates period validation, cache hit/miss detection, and analytics retrieval for {@code GET /api/dashboard/summary}.
 */
@Service
public class DashboardService {

    private static final int MIN_MONTHS_BACK = 1;
    private static final int MAX_MONTHS_BACK = 12;

    private final DashboardSummaryCacheService dashboardSummaryCacheService;

    public DashboardService(DashboardSummaryCacheService dashboardSummaryCacheService) {
        this.dashboardSummaryCacheService = dashboardSummaryCacheService;
    }

    public DashboardSummaryDto getSummary(UUID userId, Integer month, Integer year, int monthsBack) {
        YearMonth period = PeriodResolver.resolve(month, year);
        if (monthsBack < MIN_MONTHS_BACK || monthsBack > MAX_MONTHS_BACK) {
            throw new BusinessValidationException("Parameter 'monthsBack' harus antara 1 sampai 12");
        }

        AtomicBoolean cacheMissMarker = new AtomicBoolean(false);
        DashboardSummaryDto summary = dashboardSummaryCacheService.getOrComputeSummary(
                userId,
                period.getYear(),
                period.getMonthValue(),
                monthsBack,
                cacheMissMarker
        );

        boolean servedFromCache = !cacheMissMarker.get();
        return summary.withFromCache(servedFromCache);
    }
}
