package com.finpulse.repository;

import com.finpulse.entity.ReportExport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User-scoped persistence repository for asynchronous {@link ReportExport} jobs.
 */
public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {

    @Query("""
            SELECT r FROM ReportExport r
            WHERE r.user.id = :userId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<ReportExport> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    Optional<ReportExport> findByIdAndUserId(UUID id, UUID userId);
}
