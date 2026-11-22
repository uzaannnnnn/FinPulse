package com.finpulse.dto;

import com.finpulse.entity.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for {@code PUT /api/transactions/{id}}.
 */
public record UpdateTransactionRequest(
        @NotNull(message = "Kategori wajib dipilih")
        UUID categoryId,

        @NotNull(message = "Tipe transaksi wajib diisi")
        TransactionType type,

        @NotNull(message = "Nominal transaksi wajib diisi")
        @DecimalMin(value = "0.0", inclusive = false, message = "Nominal transaksi harus lebih besar dari 0")
        @DecimalMax(value = "9999999999999.99", message = "Nominal transaksi melebihi batas maksimal")
        @Digits(integer = 13, fraction = 2, message = "Nominal transaksi maksimal 13 digit dengan 2 angka desimal")
        BigDecimal amount,

        @Size(max = 500, message = "Deskripsi maksimal 500 karakter")
        String description,

        @NotNull(message = "Tanggal transaksi wajib diisi")
        LocalDate transactionDate,

        @Size(max = 2048, message = "URL struk maksimal 2048 karakter")
        String receiptUrl
) {
}
