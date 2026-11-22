package com.finpulse.repository;

import com.finpulse.entity.BudgetAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User-scoped persistence repository for over-budget {@link BudgetAlert} notifications.
 */
public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, UUID> {

    @Query("""
            SELECT a FROM BudgetAlert a
            JOIN FETCH a.category
            LEFT JOIN FETCH a.budget
            WHERE a.user.id = :userId
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<BudgetAlert> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT a FROM BudgetAlert a
            JOIN FETCH a.category
            LEFT JOIN FETCH a.budget
            WHERE a.user.id = :userId
              AND a.isRead = false
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<BudgetAlert> findRecentUnreadByUserId(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    @Query("""
            SELECT a FROM BudgetAlert a
            JOIN FETCH a.category
            LEFT JOIN FETCH a.budget
            WHERE a.id = :id AND a.user.id = :userId
            """)
    Optional<BudgetAlert> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT a FROM BudgetAlert a
            JOIN FETCH a.category
            WHERE a.user.id = :userId
              AND a.category.id = :categoryId
              AND a.month = :month
              AND a.year = :year
              AND a.isRead = false
            ORDER BY a.createdAt DESC
            """)
    List<BudgetAlert> findUnreadByUserCategoryAndPeriod(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("month") int month,
            @Param("year") int year
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BudgetAlert a
            SET a.isRead = true, a.updatedAt = :updatedAt
            WHERE a.user.id = :userId AND a.isRead = false
            """)
    int markAllAsReadByUserId(@Param("userId") UUID userId, @Param("updatedAt") Instant updatedAt);
}
