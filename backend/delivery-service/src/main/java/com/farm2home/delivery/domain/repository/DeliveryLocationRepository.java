package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, UUID> {

    Optional<DeliveryLocation> findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(UUID deliveryAssignmentId);

    Page<DeliveryLocation> findAllByDeliveryAssignmentIdOrderByRecordedAtDesc(UUID deliveryAssignmentId, Pageable pageable);
}
