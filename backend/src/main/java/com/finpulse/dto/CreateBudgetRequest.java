package com.finpulse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/budgets}.
 */
public record CreateBudgetRequest(
        @NotNull(message = "Kategori wajib dipilih")
        UUID categoryId,

        @NotNull(message = "Bulan wajib diisi")
        @Min(value = 1, message = "Bulan harus antara 1 sampai 12")
        @Max(value = 12, message = "Bulan harus antara 1 sampai 12")
        Integer month,

        @NotNull(message = "Tahun wajib diisi")
        @Min(value = 2020, message = "Tahun minimal 2020")
        @Max(value = 2100, message = "Tahun maksimal 2100")
        Integer year,

        @NotNull(message = "Batas anggaran wajib diisi")
        @DecimalMin(value = "0.0", inclusive = false, message = "Batas anggaran harus lebih besar dari 0")
        @DecimalMax(value = "9999999999999.99", message = "Batas anggaran melebihi batas maksimal")
        @Digits(integer = 13, fraction = 2, message = "Batas anggaran maksimal 13 digit dengan 2 angka desimal")
        BigDecimal amountLimit
) {
}
