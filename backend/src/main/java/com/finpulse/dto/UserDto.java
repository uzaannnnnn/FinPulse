package com.finpulse.dto;

import com.finpulse.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Authenticated user profile projection returned in auth responses.
 */
public record UserDto(
        UUID id,
        String email,
        String fullName,
        Instant createdAt
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getCreatedAt()
        );
    }
}
