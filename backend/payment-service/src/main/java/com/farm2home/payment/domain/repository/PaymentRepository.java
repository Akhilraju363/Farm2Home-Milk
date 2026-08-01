package com.farm2home.payment.domain.repository;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdAndDeletedFalse(UUID id);
    Optional<Payment> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);
    Optional<Payment> findByPaymentReferenceAndDeletedFalse(String paymentReference);
    Optional<Payment> findByGatewayOrderIdAndDeletedFalse(String gatewayOrderId);

    List<Payment> findAllByOrderIdAndDeletedFalse(UUID orderId);

    Page<Payment> findAllByDeletedFalse(Pageable pageable);
    Page<Payment> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    boolean existsByOrderIdAndPaymentStatusAndDeletedFalse(UUID orderId, PaymentStatus status);

    /** Duplicate-payment guard: reject a new payment attempt while the order already has one
     *  that's succeeded or still in flight (PENDING) - only a FAILED/REFUNDED prior attempt
     *  leaves an order payable again. */
    boolean existsByOrderIdAndPaymentStatusInAndDeletedFalse(UUID orderId, Collection<PaymentStatus> statuses);

    @Query("""
           SELECT COALESCE(SUM(p.amount), 0)
           FROM Payment p
           WHERE p.paymentStatus = :status
             AND p.paidAt BETWEEN :start AND :end
             AND p.deleted = false
           """)
    BigDecimal sumAmountByStatusAndPaidAtBetween(@Param("status") PaymentStatus status,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /** Revenue Trend: SUM(amount) of SUCCESS payments bucketed by {@code unit} (day/week/month/year,
     *  bound as a plain text argument to Postgres' own date_trunc - never string-concatenated).
     *  The GROUP BY/SUM runs entirely in the database; the result set is at most one row per
     *  period in the requested range, never one row per payment. */
    @Query(value = """
            SELECT date_trunc(:unit, p.paid_at)::date AS period, COALESCE(SUM(p.amount), 0) AS revenue
            FROM payment.payments p
            WHERE p.payment_status = 'SUCCESS'
              AND p.is_deleted = false
              AND (CAST(:start AS timestamp) IS NULL OR p.paid_at >= :start)
              AND (CAST(:endExclusive AS timestamp) IS NULL OR p.paid_at < :endExclusive)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<RevenueTrendRow> findRevenueTrend(@Param("unit") String unit,
                                            @Param("start") LocalDateTime start,
                                            @Param("endExclusive") LocalDateTime endExclusive);

    /** Payment Analytics: per period, per status, transaction count and amount total - the
     *  service layer folds these rows into one point per period (total/success/failed counts).
     *  Grouped on created_at (not paid_at, which is null for pending/failed payments) so every
     *  payment attempt is represented, matching the Payment Report's date filter. */
    @Query(value = """
            SELECT date_trunc(:unit, p.created_at)::date AS period, p.payment_status AS status,
                   COUNT(*) AS txnCount, COALESCE(SUM(p.amount), 0) AS amount
            FROM payment.payments p
            WHERE p.is_deleted = false
              AND (CAST(:start AS timestamp) IS NULL OR p.created_at >= :start)
              AND (CAST(:endExclusive AS timestamp) IS NULL OR p.created_at < :endExclusive)
            GROUP BY period, p.payment_status
            ORDER BY period
            """, nativeQuery = true)
    List<PaymentAnalyticsRow> findPaymentAnalytics(@Param("unit") String unit,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("endExclusive") LocalDateTime endExclusive);

    /** Payment Status Synchronization: gateway-backed payments (UPI/RAZORPAY) still PENDING
     *  {@code staleAfter} minutes after creation - candidates for the reconciliation job, which
     *  re-checks each one against the gateway in case a webhook was missed or never sent. */
    @Query("""
           SELECT p.id FROM Payment p
           WHERE p.paymentStatus = com.farm2home.payment.domain.enums.PaymentStatus.PENDING
             AND p.paymentMethod IN (com.farm2home.payment.domain.enums.PaymentMethod.UPI,
                                      com.farm2home.payment.domain.enums.PaymentMethod.RAZORPAY)
             AND p.gatewayOrderId IS NOT NULL
             AND p.deleted = false
             AND p.createdAt < :cutoff
           """)
    List<UUID> findStalePendingGatewayPaymentIds(@Param("cutoff") LocalDateTime cutoff);

    interface RevenueTrendRow {
        LocalDate getPeriod();
        BigDecimal getRevenue();
    }

    interface PaymentAnalyticsRow {
        LocalDate getPeriod();
        String getStatus();
        long getTxnCount();
        BigDecimal getAmount();
    }
}
