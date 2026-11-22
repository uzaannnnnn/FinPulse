package com.finpulse.service;

import com.finpulse.dto.AlertListResponse;
import com.finpulse.dto.BudgetAlertDto;
import com.finpulse.entity.BudgetAlert;
import com.finpulse.exception.BusinessValidationException;
import com.finpulse.exception.ResourceNotFoundException;
import com.finpulse.repository.BudgetAlertRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User-isolated business logic for managing over-budget alert notifications.
 */
@Service
public class AlertService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final BudgetAlertRepository budgetAlertRepository;

    public AlertService(BudgetAlertRepository budgetAlertRepository) {
        this.budgetAlertRepository = budgetAlertRepository;
    }

    @Transactional(readOnly = true)
    public AlertListResponse listAlerts(UUID userId, boolean unreadOnly, int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessValidationException("Parameter 'limit' harus antara 1 sampai 100");
        }

        PageRequest pageRequest = PageRequest.of(0, limit);
        List<BudgetAlert> alerts = unreadOnly
                ? budgetAlertRepository.findRecentUnreadByUserId(userId, pageRequest)
                : budgetAlertRepository.findRecentByUserId(userId, pageRequest);
        long unreadCount = (unreadOnly && alerts.size() < limit)
                ? alerts.size()
                : budgetAlertRepository.countByUserIdAndIsReadFalse(userId);

        List<BudgetAlertDto> dtos = alerts.stream()
                .map(BudgetAlertDto::from)
                .toList();

        return new AlertListResponse(unreadCount, dtos);
    }

    @Transactional
    public BudgetAlertDto markAsRead(UUID userId, UUID alertId) {
        BudgetAlert alert = budgetAlertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notifikasi tidak ditemukan"));

        if (!alert.isRead()) {
            alert.setRead(true);
            alert = budgetAlertRepository.saveAndFlush(alert);
        }
        return BudgetAlertDto.from(alert);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        budgetAlertRepository.markAllAsReadByUserId(userId, Instant.now());
    }

    @Transactional
    public void deleteAlert(UUID userId, UUID alertId) {
        BudgetAlert alert = budgetAlertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notifikasi tidak ditemukan"));
        budgetAlertRepository.delete(alert);
    }
}
