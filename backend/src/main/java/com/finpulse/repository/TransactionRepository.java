package com.finpulse.repository;

import com.finpulse.entity.Transaction;
import com.finpulse.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User-scoped persistence and analytics aggregation repository for {@link Transaction} entries.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    interface TypeTotalProjection {
        TransactionType getType();

        BigDecimal getTotal();
    }

    interface CategoryExpenseProjection {
        UUID getCategoryId();

        String getCategoryName();

        String getCategoryColor();

        String getCategoryIcon();

        BigDecimal getTotal();
    }

    interface MonthlyTypeTotalProjection {
        Integer getTxYear();

        Integer getTxMonth();

        TransactionType getType();

        BigDecimal getTotal();
    }

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.category
            WHERE t.id = :id AND t.user.id = :userId
            """)
    Optional<Transaction> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT t.type AS type, SUM(t.amount) AS total
            FROM Transaction t
            WHERE t.user.id = :userId
            GROUP BY t.type
            """)
    List<TypeTotalProjection> sumByTypeForUser(@Param("userId") UUID userId);

    @Query("""
            SELECT t.type AS type, SUM(t.amount) AS total
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            GROUP BY t.type
            """)
    List<TypeTotalProjection> sumByTypeForUserInRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT COUNT(t)
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            """)
    long countByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT c.id AS categoryId,
                   c.name AS categoryName,
                   c.color AS categoryColor,
                   c.icon AS categoryIcon,
                   SUM(t.amount) AS total
            FROM Transaction t
            JOIN t.category c
            WHERE t.user.id = :userId
              AND t.type = :type
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            GROUP BY c.id, c.name, c.color, c.icon
            ORDER BY SUM(t.amount) DESC, c.name ASC
            """)
    List<CategoryExpenseProjection> sumByCategoryForUserAndTypeInRange(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT SUM(t.amount)
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.category.id = :categoryId
              AND t.type = :type
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            """)
    BigDecimal sumAmountByUserIdAndCategoryIdAndTypeInRange(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT YEAR(t.transactionDate) AS txYear,
                   MONTH(t.transactionDate) AS txMonth,
                   t.type AS type,
                   SUM(t.amount) AS total
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            GROUP BY YEAR(t.transactionDate), MONTH(t.transactionDate), t.type
            """)
    List<MonthlyTypeTotalProjection> sumMonthlyCashflowForUserInRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.category
            WHERE t.user.id = :userId
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            ORDER BY t.transactionDate DESC, t.createdAt DESC, t.id DESC
            """)
    List<Transaction> findRecentByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.category
            WHERE t.user.id = :userId
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            ORDER BY t.transactionDate ASC, t.createdAt ASC, t.id ASC
            """)
    List<Transaction> findAllByUserIdAndDateRangeOrderByDateAsc(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
