package com.finpulse.messaging;

import com.finpulse.entity.Budget;
import com.finpulse.entity.BudgetAlert;
import com.finpulse.entity.TransactionType;
import com.finpulse.repository.BudgetAlertRepository;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * RabbitMQ consumer evaluating monthly category budgets and persisting deduplicated over-budget alerts.
 */
@Component
public class OverBudgetAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(OverBudgetAlertConsumer.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_UTILIZATION_PERCENTAGE = new BigDecimal("99999.99");
    private static final DateTimeFormatter PERIOD_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetAlertRepository budgetAlertRepository;

    public OverBudgetAlertConsumer(
            BudgetRepository budgetRepository,
            TransactionRepository transactionRepository,
            BudgetAlertRepository budgetAlertRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.budgetAlertRepository = budgetAlertRepository;
    }

    @RabbitListener(queues = RabbitMqConfig.TRANSACTION_CREATED_QUEUE)
    @Transactional
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        if (event == null
                || event.type() != TransactionType.EXPENSE
                || event.userId() == null
                || event.categoryId() == null
                || event.transactionDate() == null) {
            return;
        }

        int month = event.transactionDate().getMonthValue();
        int year = event.transactionDate().getYear();

        Optional<Budget> budgetOpt = budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(
                event.userId(), event.categoryId(), month, year
        );
        if (budgetOpt.isEmpty()) {
            return;
        }

        Budget budget = budgetOpt.get();
        YearMonth period = YearMonth.of(year, month);
        BigDecimal rawSpent = transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeInRange(
                event.userId(),
                event.categoryId(),
                TransactionType.EXPENSE,
                period.atDay(1),
                period.atEndOfMonth()
        );
        BigDecimal spentAmount = rawSpent != null ? rawSpent.setScale(2, RoundingMode.HALF_UP) : ZERO;
        BigDecimal amountLimit = budget.getAmountLimit().setScale(2, RoundingMode.HALF_UP);

        if (spentAmount.compareTo(amountLimit) <= 0) {
            return;
        }

        BigDecimal rawUtilization = amountLimit.compareTo(BigDecimal.ZERO) > 0
                ? spentAmount.multiply(HUNDRED).divide(amountLimit, 2, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal utilization = rawUtilization.compareTo(MAX_UTILIZATION_PERCENTAGE) > 0
                ? MAX_UTILIZATION_PERCENTAGE
                : rawUtilization;
        String periodLabel = period.format(PERIOD_LABEL_FORMATTER);
        String categoryName = budget.getCategory().getName();
        String message = String.format(
                Locale.US,
                "Pengeluaran kategori %s pada %s mencapai %.2f%% dari batas anggaran.",
                categoryName,
                periodLabel,
                utilization
        );

        List<BudgetAlert> existingUnread = budgetAlertRepository.findUnreadByUserCategoryAndPeriod(
                event.userId(), event.categoryId(), month, year
        );

        if (!existingUnread.isEmpty()) {
            BudgetAlert alert = existingUnread.get(0);
            alert.setBudget(budget);
            alert.setAmountLimit(amountLimit);
            alert.setSpentAmount(spentAmount);
            alert.setUtilizationPercentage(utilization);
            alert.setMessage(message);
            budgetAlertRepository.save(alert);
            log.info("Updated unread over-budget alert id={} for userId={} categoryId={} period={}/{}",
                    alert.getId(), event.userId(), event.categoryId(), month, year);
            return;
        }

        BudgetAlert created = new BudgetAlert(
                budget.getUser(),
                budget,
                budget.getCategory(),
                month,
                year,
                amountLimit,
                spentAmount,
                utilization,
                message
        );
        BudgetAlert saved = budgetAlertRepository.save(created);
        log.info("Created over-budget alert id={} for userId={} categoryId={} period={}/{}",
                saved.getId(), event.userId(), event.categoryId(), month, year);
    }
}
