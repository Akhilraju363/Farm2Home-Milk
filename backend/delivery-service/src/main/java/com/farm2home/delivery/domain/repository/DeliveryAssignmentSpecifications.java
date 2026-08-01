package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic filter predicates for the Delivery Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors OrderSpecifications in order-service, minus a notDeleted() predicate since
 * DeliveryAssignment has no soft-delete field.
 */
public final class DeliveryAssignmentSpecifications {

    private DeliveryAssignmentSpecifications() {
    }

    /** Either bound may be null - an open-ended range still narrows the query. Upper bound is the
     *  exclusive start of the day after `to`, so the whole `to` day is included. */
    public static Specification<DeliveryAssignment> assignedBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(root.get("assignedAt"), from.atStartOfDay()),
                        cb.lessThan(root.get("assignedAt"), to.plusDays(1).atStartOfDay()));
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("assignedAt"), from.atStartOfDay());
            }
            return cb.lessThan(root.get("assignedAt"), to.plusDays(1).atStartOfDay());
        };
    }

    public static Specification<DeliveryAssignment> hasStatus(AssignmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<DeliveryAssignment> hasOrderIdIn(List<UUID> orderIds) {
        return (root, query, cb) -> root.get("orderId").in(orderIds);
    }

    /** Matches findAllByDeliveryPartnerId's semantics: filters on the deliveryPartner FK's id. */
    public static Specification<DeliveryAssignment> hasDeliveryPartner(UUID partnerId) {
        return (root, query, cb) -> cb.equal(root.get("deliveryPartner").get("id"), partnerId);
    }

    /** Enterprise search: matches the assigned delivery partner's name or the route's name/area/city
     *  (case-insensitive, substring) - DeliveryAssignment itself has no free-text field, but always
     *  has both relations populated, so these path traversals are safe (no risk of excluding rows
     *  via an unexpectedly-null inner join). */
    public static Specification<DeliveryAssignment> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("deliveryPartner").get("name")), pattern),
                    cb.like(cb.lower(root.get("route").get("routeName")), pattern),
                    cb.like(cb.lower(root.get("route").get("area")), pattern),
                    cb.like(cb.lower(root.get("route").get("city")), pattern));
        };
    }
}
