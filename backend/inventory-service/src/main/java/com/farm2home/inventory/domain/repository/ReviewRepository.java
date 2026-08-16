package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByIdAndDeletedFalse(UUID id);

    Optional<Review> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    Page<Review> findAllByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    Page<Review> findAllByProductIdAndDeletedFalse(UUID productId, Pageable pageable);

    boolean existsByCustomerIdAndOrderIdAndProductIdAndDeletedFalse(UUID customerId, UUID orderId, UUID productId);

    /** Computed server-side over every active review for the product (not just the currently
     *  displayed page), so the rating summary shown on Product Details always reflects the true
     *  aggregate. A query with multiple select expressions always yields List<Object[]> from the
     *  JPA provider regardless of the declared return type, so this is declared as such rather
     *  than a bare Object[] (which Spring Data would instead wrap AS a 1-element array around the
     *  real row, not unwrap). No GROUP BY, so this always returns exactly one row: [averageRating,
     *  totalReviews, count(1), count(2), ..., count(5)]. */
    @Query("""
            SELECT AVG(r.rating), COUNT(r),
                   SUM(CASE WHEN r.rating = 1 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.rating = 2 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.rating = 3 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.rating = 4 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.rating = 5 THEN 1 ELSE 0 END)
            FROM Review r
            WHERE r.productId = :productId AND r.deleted = false
            """)
    List<Object[]> ratingSummaryRaw(@Param("productId") UUID productId);
}
