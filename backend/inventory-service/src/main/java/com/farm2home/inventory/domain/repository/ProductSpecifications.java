package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.Product;
import com.farm2home.inventory.domain.entity.ProductCategory;
import com.farm2home.inventory.domain.enums.ProductStockStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dynamic filter predicates for Product search - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors customer-service's CustomerSpecifications.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<Product> createdBetween(LocalDate from, LocalDate to) {
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

    public static Specification<Product> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Product> hasCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** IN_STOCK/LOW_STOCK/OUT_OF_STOCK are derived from stockQuantity vs minimumStockQuantity
     *  (see ProductMapper.computeDerivedFields) - expressed here as the same comparison so
     *  filtering by stock status stays consistent with what the response actually reports. */
    public static Specification<Product> hasStockStatus(ProductStockStatus status) {
        return (root, query, cb) -> switch (status) {
            case OUT_OF_STOCK -> cb.lessThanOrEqualTo(root.get("stockQuantity"), 0);
            case LOW_STOCK -> cb.and(
                    cb.greaterThan(root.get("stockQuantity"), 0),
                    cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("minimumStockQuantity")));
            case IN_STOCK -> cb.greaterThan(root.get("stockQuantity"), root.get("minimumStockQuantity"));
        };
    }

    /** Mirrors ProductMapper's availability derivation: active AND in stock. */
    public static Specification<Product> isAvailable(boolean available) {
        return (root, query, cb) -> {
            var activeAndInStock = cb.and(cb.isTrue(root.get("active")), cb.greaterThan(root.get("stockQuantity"), 0));
            return available ? activeAndInStock : cb.not(activeAndInStock);
        };
    }

    /** Enterprise search: matches name, description, or category name (case-insensitive, substring). */
    public static Specification<Product> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            var category = root.join("category", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(category.get("name")), pattern));
        };
    }
}
