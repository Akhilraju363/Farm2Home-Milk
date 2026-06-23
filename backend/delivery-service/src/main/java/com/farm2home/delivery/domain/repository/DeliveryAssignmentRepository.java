package com.farm2home.delivery.domain.repository;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    Page<DeliveryAssignment> findAll(Pageable pageable);
    Page<DeliveryAssignment> findAllByDeliveryPartnerId(UUID partnerId, Pageable pageable);

    Optional<DeliveryAssignment> findByIdAndDeliveryPartnerId(UUID id, UUID partnerId);

    List<DeliveryAssignment> findAllByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    Page<DeliveryAssignment> findAllByStatus(AssignmentStatus status, Pageable pageable);
}
