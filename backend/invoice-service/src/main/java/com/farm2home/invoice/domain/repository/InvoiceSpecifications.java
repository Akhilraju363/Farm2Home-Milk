package com.farm2home.invoice.domain.repository;

import com.farm2home.invoice.domain.entity.Invoice;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dynamic filter predicates for Invoice search - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied, matching farm-service's FarmSpecifications
 * convention. Deliberately NOT a single JPQL @Query with "(:param IS NULL OR ...)" branches: that
 * pattern hit a real bug here - Postgres cannot infer a bind parameter's type when it only ever
 * appears inside a null-check/function context, and fails the whole prepared statement with
 * "could not determine data type of parameter $N" the moment a filter is omitted. Specifications
 * sidestep this entirely since a predicate is either composed with a real, concretely-typed Java
 * value, or never added to the query at all.
 */
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    public static Specification<Invoice> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Invoice> hasKeyword(String keyword) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("invoiceNumber")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Invoice> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<Invoice> issuedBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("issueDate"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("issueDate"), from);
            }
            return cb.lessThanOrEqualTo(root.get("issueDate"), to);
        };
    }
}
