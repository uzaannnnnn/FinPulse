package com.finpulse.service;

import com.finpulse.dto.CategoryDto;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Provides user-scoped and global system category lookups.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories(UUID userId, TransactionType type) {
        List<Category> categories = type == null
                ? categoryRepository.findAllAccessibleByUserId(userId)
                : categoryRepository.findAllAccessibleByUserIdAndType(userId, type);

        return categories.stream()
                .map(CategoryDto::from)
                .toList();
    }
}
