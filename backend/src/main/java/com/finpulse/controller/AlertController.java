package com.finpulse.controller;

import com.finpulse.dto.AlertListResponse;
import com.finpulse.dto.BudgetAlertDto;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for listing, acknowledging, and deleting user over-budget alerts.
 */
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Notifikasi over-budget hasil consumer RabbitMQ")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(
            operationId = "listAlerts",
            summary = "Ambil daftar notifikasi over-budget milik pengguna aktif"
    )
    public ResponseEntity<AlertListResponse> listAlerts(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(alertService.listAlerts(principal.getId(), unreadOnly, limit));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            operationId = "markAlertAsRead",
            summary = "Tandai satu notifikasi over-budget milik pengguna sebagai sudah dibaca"
    )
    public ResponseEntity<BudgetAlertDto> markAlertAsRead(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(alertService.markAsRead(principal.getId(), id));
    }

    @PatchMapping("/read-all")
    @Operation(
            operationId = "markAllAlertsAsRead",
            summary = "Tandai seluruh notifikasi over-budget milik pengguna sebagai sudah dibaca"
    )
    public ResponseEntity<Void> markAllAlertsAsRead(
            @AuthenticationPrincipal FinPulseUserPrincipal principal
    ) {
        alertService.markAllAsRead(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(
            operationId = "deleteAlert",
            summary = "Hapus satu notifikasi over-budget milik pengguna"
    )
    public ResponseEntity<Void> deleteAlert(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable("id") UUID id
    ) {
        alertService.deleteAlert(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
