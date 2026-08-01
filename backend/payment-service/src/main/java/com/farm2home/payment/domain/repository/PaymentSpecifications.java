package com.farm2home.payment.domain.repository;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dynamic filter predicates for the Payment Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors OrderSpecifications in order-service.
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. Deliberately filters
     *  on createdAt rather than paidAt, since paidAt is null for pending/failed payments and would
     *  silently exclude them from a report meant to show all payment activity in the period. */
    public static Specification<Payment> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()),
                        cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
            }
            return cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay());
        };
    }

    public static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("paymentStatus"), status);
    }

    public static Specification<Payment> hasCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }
}
