package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryPartner;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, UUID> {

    Page<DeliveryPartner> findAllByDeletedFalse(Pageable pageable);
    Optional<DeliveryPartner> findByIdAndDeletedFalse(UUID id);
    Optional<DeliveryPartner> findByUserIdAndDeletedFalse(UUID userId);
    List<DeliveryPartner> findAllByActiveTrueAndDeletedFalse();

    /** Eligible partners for automatic assignment (active, not deleted, on the given route -
     *  see PartnerSelectionServiceImpl.selectLeastLoadedPartner, the sole caller and the ONE
     *  authoritative selection algorithm). PESSIMISTIC_WRITE locks every returned row for the rest
     *  of the caller's transaction: a second transaction racing to auto-assign another order on
     *  the SAME route blocks here until the first commits (its new DeliveryAssignment becomes
     *  visible), rather than both transactions reading the same pre-assignment workload count and
     *  picking the same "least loaded" partner - the exact race this method exists to prevent (see
     *  PartnerSelectionServiceImpl's own Javadoc for the full reasoning). Ordered by id for a
     *  stable lock-acquisition order across concurrent callers on the same route. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM DeliveryPartner p
            WHERE p.route.id = :routeId AND p.active = true AND p.deleted = false
            ORDER BY p.id
            """)
    List<DeliveryPartner> findEligiblePartnersForRouteForUpdate(@Param("routeId") UUID routeId);
}
