package com.finpulse.repository;

import com.finpulse.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User-scoped persistence repository for monthly {@link Budget} limits.
 */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @Query("""
            SELECT b FROM Budget b
            JOIN FETCH b.category
            WHERE b.user.id = :userId
              AND b.month = :month
              AND b.year = :year
            ORDER BY b.category.name ASC
            """)
    List<Budget> findAllByUserIdAndMonthAndYear(
            @Param("userId") UUID userId,
            @Param("month") int month,
            @Param("year") int year
    );

    @Query("""
            SELECT b FROM Budget b
            JOIN FETCH b.category
            WHERE b.id = :id AND b.user.id = :userId
            """)
    Optional<Budget> findByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    boolean existsByUserIdAndCategoryIdAndMonthAndYear(
            UUID userId,
            UUID categoryId,
            Integer month,
            Integer year
    );

    @Query("""
            SELECT b FROM Budget b
            JOIN FETCH b.category
            JOIN FETCH b.user
            WHERE b.user.id = :userId
              AND b.category.id = :categoryId
              AND b.month = :month
              AND b.year = :year
            """)
    Optional<Budget> findByUserIdAndCategoryIdAndMonthAndYear(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("month") int month,
            @Param("year") int year
    );
}
