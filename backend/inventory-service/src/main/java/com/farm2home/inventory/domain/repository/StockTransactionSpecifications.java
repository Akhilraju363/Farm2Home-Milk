package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.StockTransaction;
import com.farm2home.inventory.domain.enums.TxnType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dynamic filter predicates for the Inventory Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors OrderSpecifications in order-service (the validated reference).
 */
public final class StockTransactionSpecifications {

    private StockTransactionSpecifications() {
    }

    /** Either bound may be null - an open-ended range still narrows the query. Upper bound is
     *  exclusive (day after `to`) since transactedAt is a LocalDateTime, not a LocalDate. */
    public static Specification<StockTransaction> transactedBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            LocalDateTime lower = from != null ? from.atStartOfDay() : null;
            LocalDateTime upper = to != null ? to.plusDays(1).atStartOfDay() : null;
            if (lower != null && upper != null) {
                return cb.and(cb.greaterThanOrEqualTo(root.get("transactedAt"), lower),
                        cb.lessThan(root.get("transactedAt"), upper));
            }
            if (lower != null) {
                return cb.greaterThanOrEqualTo(root.get("transactedAt"), lower);
            }
            return cb.lessThan(root.get("transactedAt"), upper);
        };
    }

    public static Specification<StockTransaction> hasTxnType(TxnType txnType) {
        return (root, query, cb) -> cb.equal(root.get("txnType"), txnType);
    }

    public static Specification<StockTransaction> hasItem(UUID itemId) {
        return (root, query, cb) -> cb.equal(root.get("item").get("id"), itemId);
    }
}
