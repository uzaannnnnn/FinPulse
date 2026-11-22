package com.finpulse.controller;

import com.finpulse.dto.CategoryDto;
import com.finpulse.entity.TransactionType;
import com.finpulse.security.FinPulseUserPrincipal;
import com.finpulse.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for listing system default and user-defined financial categories.
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Daftar kategori pemasukan dan pengeluaran")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ambil daftar kategori sistem dan kategori milik pengguna")
    public ResponseEntity<List<CategoryDto>> getCategories(
            @AuthenticationPrincipal FinPulseUserPrincipal principal,
            @RequestParam(name = "type", required = false) TransactionType type
    ) {
        return ResponseEntity.ok(categoryService.getCategories(principal.getId(), type));
    }
}
