package com.farm2home.production.domain.repository;

import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.dto.response.DailySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilkProductionRepository extends JpaRepository<MilkProduction, UUID>,
        JpaSpecificationExecutor<MilkProduction> {

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

    @Query("SELECT COALESCE(SUM(m.quantityLiters), 0) FROM MilkProduction m WHERE m.collectionDate = :date AND m.deleted = false")
    BigDecimal sumQuantityByCollectionDate(@Param("date") LocalDate date);

    // Uses CAST(expr AS type), not Postgres' expr::type shorthand - Hibernate's native-query
    // parameter parser misreads the second ':' in '::' as the start of a malformed named
    // parameter, which Postgres then rejects with a syntax error at the leftover ':'.
    @Query(value = """
            SELECT CAST(date_trunc(:unit, CAST(m.collection_date AS timestamp)) AS date) AS period,
                   COALESCE(SUM(m.quantity_liters), 0) AS totalLiters
            FROM production.milk_production m
            WHERE m.is_deleted = false
              AND (CAST(:start AS date) IS NULL OR m.collection_date >= :start)
              AND (CAST(:end AS date) IS NULL OR m.collection_date <= :end)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<ProductionTrendRow> findProductionTrend(@Param("unit") String unit,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);

    interface ProductionTrendRow {
        LocalDate getPeriod();
        BigDecimal getTotalLiters();
    }
}
