package com.finpulse.dto;

import java.util.List;

/**
 * Response wrapper returned by {@code GET /api/alerts}.
 */
public record AlertListResponse(
        long unreadCount,
        List<BudgetAlertDto> alerts
) {
}
