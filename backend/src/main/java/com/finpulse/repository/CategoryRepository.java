package com.finpulse.repository;

import com.finpulse.entity.Category;
import com.finpulse.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence repository for system default and user-owned {@link Category} entries.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("""
            SELECT c FROM Category c
            WHERE c.id = :categoryId
              AND (c.user IS NULL OR c.user.id = :userId)
            """)
    Optional<Category> findAccessibleByIdAndUserId(
            @Param("categoryId") UUID categoryId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT c FROM Category c
            WHERE c.user IS NULL OR c.user.id = :userId
            ORDER BY c.isDefault DESC, c.name ASC
            """)
    List<Category> findAllAccessibleByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT c FROM Category c
            WHERE (c.user IS NULL OR c.user.id = :userId)
              AND c.type = :type
            ORDER BY c.isDefault DESC, c.name ASC
            """)
    List<Category> findAllAccessibleByUserIdAndType(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type
    );
}
