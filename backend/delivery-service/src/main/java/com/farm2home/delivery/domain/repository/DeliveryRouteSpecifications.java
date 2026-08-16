package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryRoute;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic filter predicates for Route search - combined via Specification.where(...).and(...),
 * mirrors farm-service's FarmSpecifications/customer-service's CustomerSpecifications.
 */
public final class DeliveryRouteSpecifications {

    private DeliveryRouteSpecifications() {
    }

    public static Specification<DeliveryRoute> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<DeliveryRoute> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /** Matches route name, route code, area, city, or pincode (case-insensitive, substring). */
    public static Specification<DeliveryRoute> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("routeName")), pattern),
                    cb.like(cb.lower(root.get("routeCode")), pattern),
                    cb.like(cb.lower(root.get("area")), pattern),
                    cb.like(cb.lower(root.get("city")), pattern),
                    cb.like(cb.lower(root.get("pincode")), pattern));
        };
    }
}
