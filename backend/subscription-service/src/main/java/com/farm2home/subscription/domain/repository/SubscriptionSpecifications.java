package com.farm2home.subscription.domain.repository;

import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dynamic filter predicates for the Subscription Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors order-service's OrderSpecifications, the first use of this pattern.
 */
public final class SubscriptionSpecifications {

    private SubscriptionSpecifications() {
    }

    public static Specification<Subscription> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<Subscription> startDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("startDate"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("startDate"), from);
            }
            return cb.lessThanOrEqualTo(root.get("startDate"), to);
        };
    }

    public static Specification<Subscription> hasStatus(SubscriptionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Subscription> hasCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Subscription> hasMilkType(MilkType milkType) {
        return (root, query, cb) -> cb.equal(root.get("milkType"), milkType);
    }

    /** Enterprise search: matches milk type or schedule type (case-insensitive, substring) - this
     *  entity has no free-text field, so keyword search targets its two enum-like text concepts. */
    public static Specification<Subscription> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("milkType").as(String.class)), pattern),
                    cb.like(cb.lower(root.get("scheduleType").as(String.class)), pattern));
        };
    }
}
