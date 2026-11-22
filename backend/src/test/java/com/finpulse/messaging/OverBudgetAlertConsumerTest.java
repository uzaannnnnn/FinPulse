package com.finpulse.messaging;

import com.finpulse.entity.Budget;
import com.finpulse.entity.BudgetAlert;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.entity.User;
import com.finpulse.repository.BudgetAlertRepository;
import com.finpulse.repository.BudgetRepository;
import com.finpulse.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverBudgetAlertConsumerTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetAlertRepository budgetAlertRepository;

    private OverBudgetAlertConsumer consumer;

    private UUID userId;
    private UUID categoryId;
    private User user;
    private Category foodCategory;
    private Budget budget;

    @BeforeEach
    void setUp() {
        consumer = new OverBudgetAlertConsumer(budgetRepository, transactionRepository, budgetAlertRepository);

        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        user = new User("budi@finpulse.app", "hashed", "Budi");
        user.setId(userId);
        foodCategory = new Category(null, "Makanan & Minuman", TransactionType.EXPENSE, "utensils", "#F59E0B", true);
        foodCategory.setId(categoryId);
        budget = new Budget(user, foodCategory, 11, 2026, new BigDecimal("2000000.00"));
        budget.setId(UUID.randomUUID());
    }

    @Test
    void shouldCreateUnreadAlertWhenExpenseExceedsBudgetLimit() {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                UUID.randomUUID(), userId, categoryId, TransactionType.EXPENSE,
                new BigDecimal("500000.00"), LocalDate.of(2026, 11, 15)
        );

        when(budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(userId, categoryId, 11, 2026))
                .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeInRange(
                userId, categoryId, TransactionType.EXPENSE,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(new BigDecimal("2350000.00"));
        when(budgetAlertRepository.findUnreadByUserCategoryAndPeriod(userId, categoryId, 11, 2026))
                .thenReturn(List.of());
        when(budgetAlertRepository.save(any(BudgetAlert.class))).thenAnswer(inv -> {
            BudgetAlert saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        consumer.handleTransactionCreated(event);

        ArgumentCaptor<BudgetAlert> captor = ArgumentCaptor.forClass(BudgetAlert.class);
        verify(budgetAlertRepository).save(captor.capture());
        BudgetAlert alert = captor.getValue();
        assertThat(alert.getAmountLimit()).isEqualByComparingTo("2000000.00");
        assertThat(alert.getSpentAmount()).isEqualByComparingTo("2350000.00");
        assertThat(alert.getUtilizationPercentage()).isEqualByComparingTo("117.50");
        assertThat(alert.isRead()).isFalse();
        assertThat(alert.getMessage()).contains("Makanan & Minuman").contains("Nov 2026").contains("117.50%");
    }

    @Test
    void shouldUpdateExistingUnreadAlertInsteadOfCreatingDuplicate() {
        BudgetAlert existing = new BudgetAlert(
                user, budget, foodCategory, 11, 2026,
                new BigDecimal("2000000.00"), new BigDecimal("2100000.00"),
                new BigDecimal("105.00"), "Old message"
        );
        existing.setId(UUID.randomUUID());

        TransactionCreatedEvent event = new TransactionCreatedEvent(
                UUID.randomUUID(), userId, categoryId, TransactionType.EXPENSE,
                new BigDecimal("400000.00"), LocalDate.of(2026, 11, 20)
        );

        when(budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(userId, categoryId, 11, 2026))
                .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeInRange(
                userId, categoryId, TransactionType.EXPENSE,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(new BigDecimal("2500000.00"));
        when(budgetAlertRepository.findUnreadByUserCategoryAndPeriod(userId, categoryId, 11, 2026))
                .thenReturn(List.of(existing));
        when(budgetAlertRepository.save(existing)).thenReturn(existing);

        consumer.handleTransactionCreated(event);

        verify(budgetAlertRepository).save(existing);
        assertThat(existing.getSpentAmount()).isEqualByComparingTo("2500000.00");
        assertThat(existing.getUtilizationPercentage()).isEqualByComparingTo("125.00");
        assertThat(existing.getMessage()).contains("125.00%");
    }

    @Test
    void shouldIgnoreIncomeTransactionsAndWithinLimitExpenses() {
        TransactionCreatedEvent incomeEvent = new TransactionCreatedEvent(
                UUID.randomUUID(), userId, categoryId, TransactionType.INCOME,
                new BigDecimal("5000000.00"), LocalDate.of(2026, 11, 1)
        );
        consumer.handleTransactionCreated(incomeEvent);
        verify(budgetRepository, never()).findByUserIdAndCategoryIdAndMonthAndYear(any(), any(), any(Integer.class), any(Integer.class));
        verify(budgetAlertRepository, never()).save(any());

        TransactionCreatedEvent withinBudgetEvent = new TransactionCreatedEvent(
                UUID.randomUUID(), userId, categoryId, TransactionType.EXPENSE,
                new BigDecimal("500000.00"), LocalDate.of(2026, 11, 5)
        );
        when(budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(userId, categoryId, 11, 2026))
                .thenReturn(Optional.of(budget));
        when(transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeInRange(
                userId, categoryId, TransactionType.EXPENSE,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(new BigDecimal("2000000.00"));

        consumer.handleTransactionCreated(withinBudgetEvent);
        verify(budgetAlertRepository, never()).save(any());
    }

    @Test
    void shouldClampExtremeUtilizationToFitNumeric7Scale2AndIgnoreWhenNoBudgetExists() {
        Budget tinyBudget = new Budget(user, foodCategory, 11, 2026, new BigDecimal("100.00"));
        tinyBudget.setId(UUID.randomUUID());

        TransactionCreatedEvent extremeEvent = new TransactionCreatedEvent(
                UUID.randomUUID(), userId, categoryId, TransactionType.EXPENSE,
                new BigDecimal("50000000.00"), LocalDate.of(2026, 11, 10)
        );
        when(budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(userId, categoryId, 11, 2026))
                .thenReturn(Optional.of(tinyBudget));
        when(transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeInRange(
                userId, categoryId, TransactionType.EXPENSE,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        )).thenReturn(new BigDecimal("50000000.00"));
        when(budgetAlertRepository.findUnreadByUserCategoryAndPeriod(userId, categoryId, 11, 2026))
                .thenReturn(List.of());
        when(budgetAlertRepository.save(any(BudgetAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.handleTransactionCreated(extremeEvent);

        ArgumentCaptor<BudgetAlert> captor = ArgumentCaptor.forClass(BudgetAlert.class);
        verify(budgetAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getUtilizationPercentage()).isEqualByComparingTo("99999.99");

        // When no budget exists for period -> no-op
        TransactionCreatedEvent noBudgetEvent = new TransactionCreatedEvent(
                UUID.randomUUID(), userId, categoryId, TransactionType.EXPENSE,
                new BigDecimal("100000.00"), LocalDate.of(2026, 12, 1)
        );
        when(budgetRepository.findByUserIdAndCategoryIdAndMonthAndYear(userId, categoryId, 12, 2026))
                .thenReturn(Optional.empty());

        consumer.handleTransactionCreated(noBudgetEvent);
        verify(budgetAlertRepository).save(any(BudgetAlert.class));
    }
}
