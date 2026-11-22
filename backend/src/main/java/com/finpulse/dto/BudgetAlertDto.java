package com.finpulse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.finpulse.entity.BudgetAlert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Over-budget notification payload returned by {@code /api/alerts}.
 */
public record BudgetAlertDto(
        UUID id,
        UUID budgetId,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        int month,
        int year,
        BigDecimal amountLimit,
        BigDecimal spentAmount,
        BigDecimal utilizationPercentage,
        String message,
        @JsonProperty("isRead") boolean isRead,
        Instant createdAt,
        Instant updatedAt
) {

    public static BudgetAlertDto from(BudgetAlert alert) {
        return new BudgetAlertDto(
                alert.getId(),
                alert.getBudget() != null ? alert.getBudget().getId() : null,
                alert.getCategory().getId(),
                alert.getCategory().getName(),
                alert.getCategory().getColor(),
                alert.getMonth(),
                alert.getYear(),
                alert.getAmountLimit(),
                alert.getSpentAmount(),
                alert.getUtilizationPercentage(),
                alert.getMessage(),
                alert.isRead(),
                alert.getCreatedAt(),
                alert.getUpdatedAt()
        );
    }
}
