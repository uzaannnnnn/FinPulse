package com.finpulse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Monthly budget projection with calculated realization metrics exposed by {@code /api/budgets}.
 */
public record BudgetDto(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        int month,
        int year,
        BigDecimal amountLimit,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        BigDecimal utilizationPercentage,
        @JsonProperty("isOverBudget") boolean isOverBudget,
        Instant createdAt,
        Instant updatedAt
) {
}
