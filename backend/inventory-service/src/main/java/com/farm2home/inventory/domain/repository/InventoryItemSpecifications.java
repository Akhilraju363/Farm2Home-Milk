package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.enums.ItemType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Dynamic filter predicates for InventoryItem search - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors customer-service's CustomerSpecifications.
 */
public final class InventoryItemSpecifications {

    private InventoryItemSpecifications() {
    }

    public static Specification<InventoryItem> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<InventoryItem> createdBetween(LocalDate from, LocalDate to) {
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

    public static Specification<InventoryItem> hasItemType(ItemType itemType) {
        return (root, query, cb) -> cb.equal(root.get("itemType"), itemType);
    }

    /** Enterprise search: matches item name or supplier (case-insensitive, substring). */
    public static Specification<InventoryItem> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("itemName")), pattern),
                    cb.like(cb.lower(root.get("supplier")), pattern));
        };
    }
}
