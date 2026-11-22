package com.finpulse.dto;

import com.finpulse.validation.TrimmedSize;
import com.finpulse.validation.Utf8MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new FinPulse user account.
 */
public record RegisterRequest(
        @NotBlank(message = "Nama lengkap wajib diisi")
        @Size(min = 2, max = 100, message = "Nama lengkap harus antara 2 hingga 100 karakter")
        @TrimmedSize(min = 2, max = 100, message = "Nama lengkap harus antara 2 hingga 100 karakter")
        String fullName,

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        @Size(max = 255, message = "Email maksimal 255 karakter")
        String email,

        @NotBlank(message = "Password wajib diisi")
        @Size(min = 8, max = 72, message = "Password minimal 8 dan maksimal 72 karakter")
        @Utf8MaxBytes(max = 72, message = "Password maksimal 72 byte UTF-8")
        String password
) {
}
