package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID>,
        JpaSpecificationExecutor<StockTransaction> {

    Page<StockTransaction> findAllByItemIdOrderByTransactedAtDesc(UUID itemId, Pageable pageable);

    Optional<StockTransaction> findByIdAndItemId(UUID id, UUID itemId);

    @Query(value = """
            SELECT date_trunc(:unit, t.transacted_at)::date AS period, COALESCE(SUM(t.quantity), 0) AS consumedQuantity
            FROM inventory.stock_transactions t
            WHERE t.txn_type = 'OUT'
              AND (CAST(:start AS timestamp) IS NULL OR t.transacted_at >= :start)
              AND (CAST(:endExclusive AS timestamp) IS NULL OR t.transacted_at < :endExclusive)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<InventoryConsumptionRow> findConsumptionTrend(@Param("unit") String unit,
                                                         @Param("start") LocalDateTime start,
                                                         @Param("endExclusive") LocalDateTime endExclusive);

    interface InventoryConsumptionRow {
        LocalDate getPeriod();
        BigDecimal getConsumedQuantity();
    }
}
