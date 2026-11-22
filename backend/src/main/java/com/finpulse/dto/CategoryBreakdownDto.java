package com.finpulse.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-category expense breakdown item for {@code DashboardSummaryDto}.
 */
public record CategoryBreakdownDto(
        UUID categoryId,
        String categoryName,
        String categoryColor,
        String categoryIcon,
        BigDecimal amount,
        BigDecimal percentage
) {
}
