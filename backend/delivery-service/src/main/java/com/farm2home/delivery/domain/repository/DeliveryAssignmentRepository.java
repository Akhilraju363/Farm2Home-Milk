package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID>,
        JpaSpecificationExecutor<DeliveryAssignment> {

    Page<DeliveryAssignment> findAll(Pageable pageable);
    Page<DeliveryAssignment> findAllByDeliveryPartnerId(UUID partnerId, Pageable pageable);

    Optional<DeliveryAssignment> findByIdAndDeliveryPartnerId(UUID id, UUID partnerId);

    List<DeliveryAssignment> findAllByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    /** Live workload - number of non-terminal (ASSIGNED/OUT_FOR_DELIVERY) assignments for one
     *  partner. Used by PartnerSelectionServiceImpl (least-loaded selection) and exposed
     *  read-only on PartnerResponse/AssignmentResponse for admin visibility - never persisted as
     *  its own column, always computed live so it can never drift from the assignments it counts. */
    long countByDeliveryPartner_IdAndStatusIn(UUID partnerId, List<AssignmentStatus> statuses);

    /** Route-deletion guard (see DeliveryRouteServiceImpl.delete()) - a route referenced by a
     *  non-terminal (ASSIGNED/OUT_FOR_DELIVERY) assignment cannot be deleted. Historical
     *  assignments referencing a DELIVERED/FAILED route are untouched either way. */
    boolean existsByRoute_IdAndStatusIn(UUID routeId, List<AssignmentStatus> statuses);

    Page<DeliveryAssignment> findAllByStatus(AssignmentStatus status, Pageable pageable);

    long countByStatusAndDeliveredAtBetween(AssignmentStatus status, LocalDateTime start, LocalDateTime end);

    /** Delivery Performance: per period, per status, delivery count - the service layer folds
     *  these rows into one point per period (total/completed/failed counts). The GROUP BY/COUNT
     *  runs entirely in Postgres; the result set is at most a few rows per period, never one
     *  row per assignment. */
    // Uses CAST(expr AS date), not Postgres' expr::date shorthand - Hibernate's native-query
    // parameter parser misreads the second ':' in '::' as the start of a malformed named
    // parameter, which Postgres then rejects with a syntax error at the leftover ':'.
    @Query(value = """
            SELECT CAST(date_trunc(:unit, a.assigned_at) AS date) AS period, a.status AS status, COUNT(*) AS cnt
            FROM delivery.delivery_assignments a
            WHERE (CAST(:start AS timestamp) IS NULL OR a.assigned_at >= :start)
              AND (CAST(:endExclusive AS timestamp) IS NULL OR a.assigned_at < :endExclusive)
            GROUP BY period, a.status
            ORDER BY period
            """, nativeQuery = true)
    List<DeliveryPerformanceRow> findPerformanceTrend(@Param("unit") String unit,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("endExclusive") LocalDateTime endExclusive);

    interface DeliveryPerformanceRow {
        LocalDate getPeriod();
        String getStatus();
        long getCnt();
    }
}
