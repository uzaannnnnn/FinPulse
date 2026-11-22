package com.finpulse.dto;

import com.finpulse.entity.Category;
import com.finpulse.entity.Transaction;
import com.finpulse.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Transaction projection exposed by {@code /api/transactions} and {@code /api/dashboard/summary}.
 */
public record TransactionDto(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate transactionDate,
        String receiptUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransactionDto from(Transaction transaction) {
        Category category = transaction.getCategory();
        return new TransactionDto(
                transaction.getId(),
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColor(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getReceiptUrl(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
