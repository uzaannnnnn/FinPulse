package com.finpulse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload containing a refresh token for token rotation or revocation.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token wajib diisi")
        String refreshToken
) {
}
