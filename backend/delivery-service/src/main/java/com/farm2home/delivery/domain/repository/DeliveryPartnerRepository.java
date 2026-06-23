package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryPartner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, UUID> {

    Page<DeliveryPartner> findAllByDeletedFalse(Pageable pageable);
    Optional<DeliveryPartner> findByIdAndDeletedFalse(UUID id);
    Optional<DeliveryPartner> findByUserIdAndDeletedFalse(UUID userId);
    List<DeliveryPartner> findAllByActiveTrueAndDeletedFalse();

    // Least-loaded partner assignment: active partner with fewest ASSIGNED/OUT_FOR_DELIVERY assignments
    @Query("""
            SELECT p FROM DeliveryPartner p
            WHERE p.active = true AND p.deleted = false
            ORDER BY (
                SELECT COUNT(a) FROM DeliveryAssignment a
                WHERE a.deliveryPartner = p AND a.status IN ('ASSIGNED', 'OUT_FOR_DELIVERY')
            ) ASC
            """)
    List<DeliveryPartner> findLeastLoadedPartners(Pageable pageable);
}
