package com.finpulse.controller;

import com.finpulse.dto.BudgetDto;
import com.finpulse.dto.CreateBudgetRequest;
import com.finpulse.dto.UpdateBudgetRequest;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing user-scoped monthly category budgets and spending realization.
 */
@RestController
@RequestMapping("/api/budgets")
@Tag(name = "Budgets", description = "Manajemen batas anggaran bulanan dan realisasi pemakaian")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(
            operationId = "listBudgets",
            summary = "Ambil daftar batas anggaran bulanan beserta realisasi pemakaian"
    )
    public ResponseEntity<List<BudgetDto>> listBudgets(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "year", required = false) Integer year
    ) {
        return ResponseEntity.ok(budgetService.listBudgets(principal.getId(), month, year));
    }

    @PostMapping
    @Operation(
            operationId = "createBudget",
            summary = "Buat batas anggaran bulanan untuk kategori pengeluaran"
    )
    public ResponseEntity<BudgetDto> createBudget(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @Valid @RequestBody CreateBudgetRequest request
    ) {
        BudgetDto created = budgetService.createBudget(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            operationId = "updateBudget",
            summary = "Perbarui nominal batas anggaran bulanan"
    )
    public ResponseEntity<BudgetDto> updateBudget(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetRequest request
    ) {
        return ResponseEntity.ok(budgetService.updateBudget(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            operationId = "deleteBudget",
            summary = "Hapus batas anggaran bulanan milik pengguna"
    )
    public ResponseEntity<Void> deleteBudget(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @PathVariable UUID id
    ) {
        budgetService.deleteBudget(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
