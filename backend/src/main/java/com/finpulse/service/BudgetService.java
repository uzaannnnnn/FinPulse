package com.finpulse.service;

import com.finpulse.dto.BudgetDto;
import com.finpulse.dto.CreateBudgetRequest;
import com.finpulse.dto.UpdateBudgetRequest;
import com.finpulse.entity.Budget;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.exception.BusinessValidationException;
import com.finpulse.exception.DuplicateBudgetException;
import com.finpulse.exception.ResourceNotFoundException;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.CategoryRepository;
import com.finpulse.repository.TransactionRepository;
import com.finpulse.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for user-isolated monthly category budgets and spending realization calculations.
 */
@Service
public class BudgetService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final DashboardCacheInvalidator dashboardCacheInvalidator;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            DashboardCacheInvalidator dashboardCacheInvalidator
    ) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.dashboardCacheInvalidator = dashboardCacheInvalidator;
    }

    @Transactional(readOnly = true)
    public List<BudgetDto> listBudgets(UUID userId, Integer month, Integer year) {
        YearMonth period = PeriodResolver.resolve(month, year);
        List<Budget> budgets = budgetRepository.findAllByUserIdAndMonthAndYear(
                userId, period.getMonthValue(), period.getYear()
        );
        if (budgets.isEmpty()) {
            return List.of();
        }

        LocalDate startDate = period.atDay(1);
        LocalDate endDate = period.atEndOfMonth();

        Map<UUID, BigDecimal> spentByCategory = transactionRepository
                .sumByCategoryForUserAndTypeInRange(userId, TransactionType.EXPENSE, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        TransactionRepository.CategoryExpenseProjection::getCategoryId,
                        p -> scaleMoney(p.getTotal())
                ));

        return budgets.stream()
                .map(budget -> toBudgetDto(
                        budget,
                        spentByCategory.getOrDefault(budget.getCategory().getId(), ZERO)
                ))
                .toList();
    }

    @Transactional
    public BudgetDto createBudget(UUID userId, CreateBudgetRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan"));

        Category category = categoryRepository.findAccessibleByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));

        if (category.getType() != TransactionType.EXPENSE) {
            throw new BusinessValidationException("Anggaran hanya dapat dibuat untuk kategori bertipe EXPENSE");
        }

        if (budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(
                userId, category.getId(), request.month(), request.year())) {
            throw new DuplicateBudgetException("Anggaran untuk kategori dan periode bulan/tahun tersebut sudah ada");
        }

        BigDecimal limit = validateAndScaleLimit(request.amountLimit());
        Budget budget = new Budget(user, category, request.month(), request.year(), limit);

        Budget saved;
        try {
            saved = budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateBudgetException("Anggaran untuk kategori dan periode bulan/tahun tersebut sudah ada");
        }

        BigDecimal spent = calculateSpentForCategoryPeriod(
                userId, category.getId(), saved.getYear(), saved.getMonth()
        );
        dashboardCacheInvalidator.evictAfterCommit(userId);
        return toBudgetDto(saved, spent);
    }

    @Transactional
    public BudgetDto updateBudget(UUID userId, UUID budgetId, UpdateBudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Anggaran tidak ditemukan"));

        budget.setAmountLimit(validateAndScaleLimit(request.amountLimit()));
        Budget saved = budgetRepository.saveAndFlush(budget);

        BigDecimal spent = calculateSpentForCategoryPeriod(
                userId, saved.getCategory().getId(), saved.getYear(), saved.getMonth()
        );
        dashboardCacheInvalidator.evictAfterCommit(userId);
        return toBudgetDto(saved, spent);
    }

    @Transactional
    public void deleteBudget(UUID userId, UUID budgetId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Anggaran tidak ditemukan"));
        budgetRepository.delete(budget);
        dashboardCacheInvalidator.evictAfterCommit(userId);
    }

    BudgetDto toBudgetDto(Budget budget, BigDecimal spentAmount) {
        Category category = budget.getCategory();
        BigDecimal limit = scaleMoney(budget.getAmountLimit());
        BigDecimal spent = scaleMoney(spentAmount);
        BigDecimal remaining = limit.subtract(spent).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilization = limit.compareTo(BigDecimal.ZERO) > 0
                ? spent.multiply(HUNDRED).divide(limit, 2, RoundingMode.HALF_UP)
                : ZERO;
        boolean isOverBudget = spent.compareTo(limit) > 0;

        return new BudgetDto(
                budget.getId(),
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColor(),
                budget.getMonth(),
                budget.getYear(),
                limit,
                spent,
                remaining,
                utilization,
                isOverBudget,
                budget.getCreatedAt(),
                budget.getUpdatedAt()
        );
    }

    private BigDecimal calculateSpentForCategoryPeriod(UUID userId, UUID categoryId, int year, int month) {
        YearMonth period = YearMonth.of(year, month);
        BigDecimal rawSpent = transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeInRange(
                userId, categoryId, TransactionType.EXPENSE, period.atDay(1), period.atEndOfMonth()
        );
        return scaleMoney(rawSpent);
    }

    private BigDecimal validateAndScaleLimit(BigDecimal amountLimit) {
        if (amountLimit == null || amountLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Batas anggaran harus lebih besar dari 0");
        }
        return scaleMoney(amountLimit);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
