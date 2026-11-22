package com.finpulse.service;

import com.finpulse.config.CacheConfig;
import com.finpulse.dto.BudgetDto;
import com.finpulse.dto.CategoryBreakdownDto;
import com.finpulse.dto.DashboardSummaryDto;
import com.finpulse.dto.MonthlyCashflowDto;
import com.finpulse.dto.TransactionDto;
import com.finpulse.entity.Budget;
import com.finpulse.entity.TransactionType;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.TransactionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cache-backed computation service for {@link DashboardSummaryDto} keyed by {@code userId:year:month:monthsBack}.
 */
@Service
public class DashboardSummaryCacheService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RECENT_TRANSACTION_LIMIT = 5;
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;

    public DashboardSummaryCacheService(
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            BudgetService budgetService
    ) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.budgetService = budgetService;
    }

    @Cacheable(
            cacheNames = CacheConfig.DASHBOARD_SUMMARY_CACHE,
            key = "#userId.toString() + ':' + #year + ':' + #month + ':' + #monthsBack"
    )
    @Transactional(readOnly = true)
    public DashboardSummaryDto getOrComputeSummary(
            UUID userId,
            int year,
            int month,
            int monthsBack,
            AtomicBoolean cacheMissMarker
    ) {
        if (cacheMissMarker != null) {
            cacheMissMarker.set(true);
        }

        YearMonth period = YearMonth.of(year, month);
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        Map<TransactionType, BigDecimal> periodTotals = toTypeTotalMap(
                transactionRepository.sumByTypeForUserInRange(userId, periodStart, periodEnd)
        );
        BigDecimal totalIncome = periodTotals.getOrDefault(TransactionType.INCOME, ZERO);
        BigDecimal totalExpense = periodTotals.getOrDefault(TransactionType.EXPENSE, ZERO);
        BigDecimal balance = totalIncome.subtract(totalExpense).setScale(2, RoundingMode.HALF_UP);

        Map<TransactionType, BigDecimal> cumulativeTotals = toTypeTotalMap(
                transactionRepository.sumByTypeForUser(userId)
        );
        BigDecimal cumulativeBalance = cumulativeTotals.getOrDefault(TransactionType.INCOME, ZERO)
                .subtract(cumulativeTotals.getOrDefault(TransactionType.EXPENSE, ZERO))
                .setScale(2, RoundingMode.HALF_UP);

        long transactionCount = transactionRepository.countByUserIdAndDateRange(userId, periodStart, periodEnd);

        List<MonthlyCashflowDto> cashflowSeries = buildCashflowSeries(userId, period, monthsBack);

        List<TransactionRepository.CategoryExpenseProjection> expenseProjections = transactionRepository
                .sumByCategoryForUserAndTypeInRange(userId, TransactionType.EXPENSE, periodStart, periodEnd);

        List<CategoryBreakdownDto> expenseByCategory = buildCategoryBreakdown(expenseProjections, totalExpense);

        Map<UUID, BigDecimal> spentByCategory = new HashMap<>();
        for (TransactionRepository.CategoryExpenseProjection projection : expenseProjections) {
            spentByCategory.put(projection.getCategoryId(), scaleMoney(projection.getTotal()));
        }

        List<Budget> budgets = budgetRepository.findAllByUserIdAndMonthAndYear(
                userId, period.getMonthValue(), period.getYear()
        );
        List<BudgetDto> budgetProgress = budgets.stream()
                .map(budget -> budgetService.toBudgetDto(
                        budget,
                        spentByCategory.getOrDefault(budget.getCategory().getId(), ZERO)
                ))
                .sorted(Comparator
                        .comparing(BudgetDto::utilizationPercentage, Comparator.reverseOrder())
                        .thenComparing(BudgetDto::categoryName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<TransactionDto> recentTransactions = transactionRepository
                .findRecentByUserIdAndDateRange(
                        userId, periodStart, periodEnd, PageRequest.of(0, RECENT_TRANSACTION_LIMIT)
                )
                .stream()
                .map(TransactionDto::from)
                .toList();

        return new DashboardSummaryDto(
                period.getMonthValue(),
                period.getYear(),
                totalIncome,
                totalExpense,
                balance,
                cumulativeBalance,
                transactionCount,
                cashflowSeries,
                expenseByCategory,
                budgetProgress,
                recentTransactions,
                Instant.now(),
                false
        );
    }

    private List<MonthlyCashflowDto> buildCashflowSeries(UUID userId, YearMonth period, int monthsBack) {
        YearMonth startMonth = period.minusMonths(monthsBack - 1L);
        LocalDate seriesStart = startMonth.atDay(1);
        LocalDate seriesEnd = period.atEndOfMonth();

        List<TransactionRepository.MonthlyTypeTotalProjection> monthlyRows = transactionRepository
                .sumMonthlyCashflowForUserInRange(userId, seriesStart, seriesEnd);

        Map<YearMonth, BigDecimal> incomeByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> expenseByMonth = new HashMap<>();
        for (TransactionRepository.MonthlyTypeTotalProjection row : monthlyRows) {
            YearMonth ym = YearMonth.of(row.getTxYear(), row.getTxMonth());
            BigDecimal amount = scaleMoney(row.getTotal());
            if (row.getType() == TransactionType.INCOME) {
                incomeByMonth.put(ym, amount);
            } else if (row.getType() == TransactionType.EXPENSE) {
                expenseByMonth.put(ym, amount);
            }
        }

        List<MonthlyCashflowDto> series = new ArrayList<>(monthsBack);
        for (int i = 0; i < monthsBack; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            BigDecimal income = incomeByMonth.getOrDefault(ym, ZERO);
            BigDecimal expense = expenseByMonth.getOrDefault(ym, ZERO);
            BigDecimal net = income.subtract(expense).setScale(2, RoundingMode.HALF_UP);
            series.add(new MonthlyCashflowDto(
                    ym.getYear(),
                    ym.getMonthValue(),
                    ym.format(MONTH_LABEL_FORMATTER),
                    income,
                    expense,
                    net
            ));
        }
        return series;
    }

    private List<CategoryBreakdownDto> buildCategoryBreakdown(
            List<TransactionRepository.CategoryExpenseProjection> projections,
            BigDecimal totalExpense
    ) {
        return projections.stream()
                .map(projection -> {
                    BigDecimal amount = scaleMoney(projection.getTotal());
                    BigDecimal percentage = totalExpense.compareTo(BigDecimal.ZERO) > 0
                            ? amount.multiply(HUNDRED).divide(totalExpense, 2, RoundingMode.HALF_UP)
                            : ZERO;
                    return new CategoryBreakdownDto(
                            projection.getCategoryId(),
                            projection.getCategoryName(),
                            projection.getCategoryColor(),
                            projection.getCategoryIcon(),
                            amount,
                            percentage
                    );
                })
                .toList();
    }

    private Map<TransactionType, BigDecimal> toTypeTotalMap(
            List<TransactionRepository.TypeTotalProjection> projections
    ) {
        Map<TransactionType, BigDecimal> totals = new EnumMap<>(TransactionType.class);
        for (TransactionRepository.TypeTotalProjection projection : projections) {
            if (projection.getType() != null) {
                totals.put(projection.getType(), scaleMoney(projection.getTotal()));
            }
        }
        return totals;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
