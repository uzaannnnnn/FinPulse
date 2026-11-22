package com.finpulse.dto;

import java.math.BigDecimal;

/**
 * Monthly income vs expense time-series point for {@code DashboardSummaryDto}.
 */
public record MonthlyCashflowDto(
        int year,
        int month,
        String label,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net
) {
}
