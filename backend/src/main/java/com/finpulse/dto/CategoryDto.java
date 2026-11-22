package com.finpulse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;

import java.util.UUID;

/**
 * Category projection exposed by {@code GET /api/categories}.
 */
public record CategoryDto(
        UUID id,
        String name,
        TransactionType type,
        String icon,
        String color,
        @JsonProperty("isDefault") boolean isDefault
) {

    public static CategoryDto from(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.getColor(),
                category.isDefault()
        );
    }
}
