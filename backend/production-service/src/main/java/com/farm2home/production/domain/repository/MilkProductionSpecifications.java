package com.farm2home.production.domain.repository;

import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.domain.enums.QualityGrade;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic filter predicates for the Production Report - combined via Specification.where(...).and(...)
 * so an arbitrary subset of filters can be applied without a combinatorial explosion of derived
 * query methods. Mirrors OrderSpecifications in order-service (the validated reference).
 */
public final class MilkProductionSpecifications {

    private MilkProductionSpecifications() {
    }

    public static Specification<MilkProduction> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    /** Either bound may be null - an open-ended range still narrows the query. */
    public static Specification<MilkProduction> collectionDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("collectionDate"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("collectionDate"), from);
            }
            return cb.lessThanOrEqualTo(root.get("collectionDate"), to);
        };
    }

    public static Specification<MilkProduction> hasQualityGrade(QualityGrade grade) {
        return (root, query, cb) -> cb.equal(root.get("qualityGrade"), grade);
    }

    public static Specification<MilkProduction> hasCowIdIn(List<UUID> cowIds) {
        return (root, query, cb) -> root.get("cowId").in(cowIds);
    }
}
