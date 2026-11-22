package com.finpulse.dto;

/**
 * Authentication response containing JWT access token, refresh token, and user details.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {

    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresInSeconds, UserDto user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
