package com.farm2home.order.domain.repository;

import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dynamic filter predicates for the Sales Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. This is the first use of Spring Data JPA Specifications in this codebase; every
 * other report's Specifications class in the other services follows this same shape.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<Order> dateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("orderDate"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("orderDate"), from);
            }
            return cb.lessThanOrEqualTo(root.get("orderDate"), to);
        };
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    /**
     * "Product" filter: orders containing at least one line item of the given milk type. Uses an
     * EXISTS subquery rather than joining root to items directly, so the root query never needs
     * DISTINCT and can't return duplicate Order rows for orders with multiple matching items.
     */
    public static Specification<Order> hasMilkType(MilkType milkType) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var itemRoot = subquery.from(OrderItem.class);
            subquery.select(cb.literal(1L))
                    .where(cb.equal(itemRoot.get("order"), root), cb.equal(itemRoot.get("milkType"), milkType));
            return cb.exists(subquery);
        };
    }

    /** Enterprise search: matches order number or notes (case-insensitive, substring). */
    public static Specification<Order> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("orderNumber")), pattern),
                    cb.like(cb.lower(root.get("notes")), pattern));
        };
    }
}
