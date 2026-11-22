package com.finpulse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for {@code PUT /api/budgets/{id}}.
 */
public record UpdateBudgetRequest(
        @NotNull(message = "Batas anggaran wajib diisi")
        @DecimalMin(value = "0.0", inclusive = false, message = "Batas anggaran harus lebih besar dari 0")
        @DecimalMax(value = "9999999999999.99", message = "Batas anggaran melebihi batas maksimal")
        @Digits(integer = 13, fraction = 2, message = "Batas anggaran maksimal 13 digit dengan 2 angka desimal")
        BigDecimal amountLimit
) {
}
