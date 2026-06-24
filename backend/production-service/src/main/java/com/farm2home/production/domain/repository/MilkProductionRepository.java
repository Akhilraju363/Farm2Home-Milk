package com.farm2home.production.domain.repository;

import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.dto.response.DailySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilkProductionRepository extends JpaRepository<MilkProduction, UUID> {

    Page<MilkProduction> findAllByDeletedFalse(Pageable pageable);

    Page<MilkProduction> findAllByCowIdAndDeletedFalse(UUID cowId, Pageable pageable);

    Optional<MilkProduction> findByIdAndDeletedFalse(UUID id);

    boolean existsByCowIdAndCollectionDateAndSessionAndDeletedFalse(
            UUID cowId, LocalDate collectionDate,
            com.farm2home.production.domain.enums.MilkSession session);

    @Query("""
            SELECT new com.farm2home.production.dto.response.DailySummaryResponse(
                m.collectionDate, SUM(m.quantityLiters), COUNT(m))
            FROM MilkProduction m
            WHERE m.cowId = :cowId
              AND m.collectionDate BETWEEN :from AND :to
              AND m.deleted = false
            GROUP BY m.collectionDate
            ORDER BY m.collectionDate
            """)
    List<DailySummaryResponse> findDailySummaryByCow(
            @Param("cowId") UUID cowId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT new com.farm2home.production.dto.response.DailySummaryResponse(
                m.collectionDate, SUM(m.quantityLiters), COUNT(m))
            FROM MilkProduction m
            WHERE m.collectionDate BETWEEN :from AND :to
              AND m.deleted = false
            GROUP BY m.collectionDate
            ORDER BY m.collectionDate
            """)
    List<DailySummaryResponse> findDailySummary(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
