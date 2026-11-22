package com.finpulse.messaging;

import com.finpulse.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted after an expense transaction is created or updated to trigger over-budget evaluation.
 */
public record TransactionCreatedEvent(
        UUID transactionId,
        UUID userId,
        UUID categoryId,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate
) {
}
