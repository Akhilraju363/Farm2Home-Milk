package com.farm2home.customer.domain.repository;

import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.domain.enums.CustomerStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Dynamic filter predicates for the Customer Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors order-service's OrderSpecifications, the first use of this pattern.
 */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<Customer> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<Customer> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var fromDateTime = from != null ? from.atStartOfDay() : null;
            var toDateTime = to != null ? to.plusDays(1).atStartOfDay() : null;
            if (fromDateTime != null && toDateTime != null) {
                return cb.and(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime),
                        cb.lessThan(root.get("createdAt"), toDateTime));
            }
            if (fromDateTime != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime);
            }
            return cb.lessThan(root.get("createdAt"), toDateTime);
        };
    }

    public static Specification<Customer> hasStatus(CustomerStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Enterprise search: matches name, mobile, email, or customer code (case-insensitive,
     *  substring). Nullable columns (email) just never match rather than needing a null guard -
     *  LOWER(NULL) LIKE '...' evaluates to NULL/false in SQL, not an error. */
    public static Specification<Customer> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern),
                    cb.like(cb.lower(root.get("mobile")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("customerCode")), pattern));
        };
    }
}
