package com.finpulse.service;

import com.finpulse.dto.CategoryDto;
import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import com.finpulse.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getCategoriesWithoutTypeShouldQueryAllAccessibleForUser() {
        UUID userId = UUID.randomUUID();
        Category category = new Category(null, "Gaji", TransactionType.INCOME, "wallet", "#10B981", true);
        category.setId(UUID.randomUUID());

        when(categoryRepository.findAllAccessibleByUserId(userId)).thenReturn(List.of(category));

        List<CategoryDto> result = categoryService.getCategories(userId, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Gaji");
        assertThat(result.getFirst().isDefault()).isTrue();
        verify(categoryRepository).findAllAccessibleByUserId(userId);
        verifyNoMoreInteractions(categoryRepository);
    }

    @Test
    void getCategoriesWithTypeShouldQueryFilteredByTypeAndUser() {
        UUID userId = UUID.randomUUID();
        Category category = new Category(null, "Transportasi", TransactionType.EXPENSE, "car", "#3B82F6", true);
        category.setId(UUID.randomUUID());

        when(categoryRepository.findAllAccessibleByUserIdAndType(userId, TransactionType.EXPENSE))
                .thenReturn(List.of(category));

        List<CategoryDto> result = categoryService.getCategories(userId, TransactionType.EXPENSE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().type()).isEqualTo(TransactionType.EXPENSE);
        verify(categoryRepository).findAllAccessibleByUserIdAndType(userId, TransactionType.EXPENSE);
        verifyNoMoreInteractions(categoryRepository);
    }
}
