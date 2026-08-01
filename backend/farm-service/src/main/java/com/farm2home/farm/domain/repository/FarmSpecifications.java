package com.farm2home.farm.domain.repository;

import com.farm2home.farm.domain.entity.Farm;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Dynamic filter predicates for Farm search - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors customer-service's CustomerSpecifications.
 */
public final class FarmSpecifications {

    private FarmSpecifications() {
    }

    public static Specification<Farm> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<Farm> createdBetween(LocalDate from, LocalDate to) {
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

    /** Enterprise search: matches farm name, owner name, location, or description
     *  (case-insensitive, substring). */
    public static Specification<Farm> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("farmName")), pattern),
                    cb.like(cb.lower(root.get("ownerName")), pattern),
                    cb.like(cb.lower(root.get("location")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern));
        };
    }
}
