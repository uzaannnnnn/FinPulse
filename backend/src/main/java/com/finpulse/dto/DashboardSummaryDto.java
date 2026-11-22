package com.finpulse.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated analytics payload returned by {@code GET /api/dashboard/summary}.
 */
public record DashboardSummaryDto(
        int month,
        int year,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        BigDecimal cumulativeBalance,
        long transactionCount,
        List<MonthlyCashflowDto> cashflowSeries,
        List<CategoryBreakdownDto> expenseByCategory,
        List<BudgetDto> budgetProgress,
        List<TransactionDto> recentTransactions,
        Instant generatedAt,
        boolean fromCache
) {

    public DashboardSummaryDto withFromCache(boolean cached) {
        return new DashboardSummaryDto(
                month,
                year,
                totalIncome,
                totalExpense,
                balance,
                cumulativeBalance,
                transactionCount,
                cashflowSeries,
                expenseByCategory,
                budgetProgress,
                recentTransactions,
                generatedAt,
                cached
        );
    }
}
