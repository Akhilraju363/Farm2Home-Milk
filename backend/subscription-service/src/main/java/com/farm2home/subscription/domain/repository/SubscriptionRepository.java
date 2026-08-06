package com.farm2home.subscription.domain.repository;

import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID>, JpaSpecificationExecutor<Subscription> {

    Page<Subscription> findAllByDeletedFalse(Pageable pageable);

    Page<Subscription> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    Optional<Subscription> findByIdAndDeletedFalse(UUID id);

    Optional<Subscription> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    boolean existsByCustomerIdAndStatusAndDeletedFalse(UUID customerId, SubscriptionStatus status);

    /** Duplicate-subscription guard: a customer may hold at most one ACTIVE subscription per
     *  milk type at a time (they can still hold separate ACTIVE subscriptions for other milk
     *  types in parallel). */
    boolean existsByCustomerIdAndMilkTypeAndStatusAndDeletedFalse(UUID customerId, MilkType milkType,
            SubscriptionStatus status);

    long countByStatusAndDeletedFalse(SubscriptionStatus status);

    List<Subscription> findAllByStatusAndEndDateBetweenAndDeletedFalse(
            SubscriptionStatus status, LocalDate startInclusive, LocalDate endInclusive);

    @Modifying
    @Query("""
           UPDATE Subscription s
           SET s.status = 'EXPIRED'
           WHERE s.endDate < :today
             AND s.status = 'ACTIVE'
             AND s.deleted = false
           """)
    int expireByEndDate(@Param("today") LocalDate today);

    // Uses CAST(expr AS type), not Postgres' expr::type shorthand - Hibernate's native-query
    // parameter parser misreads the second ':' in '::' as the start of a malformed named
    // parameter, which Postgres then rejects with a syntax error at the leftover ':'.
    @Query(value = """
            SELECT CAST(date_trunc(:unit, CAST(s.start_date AS timestamp)) AS date) AS period, COUNT(*) AS newSubscriptions
            FROM subscription.subscriptions s
            WHERE s.is_deleted = false
              AND (CAST(:start AS date) IS NULL OR s.start_date >= :start)
              AND (CAST(:end AS date) IS NULL OR s.start_date <= :end)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SubscriptionTrendRow> findSubscriptionTrend(@Param("unit") String unit,
                                                       @Param("start") LocalDate start,
                                                       @Param("end") LocalDate end);

    interface SubscriptionTrendRow {
        LocalDate getPeriod();
        long getNewSubscriptions();
    }
}
