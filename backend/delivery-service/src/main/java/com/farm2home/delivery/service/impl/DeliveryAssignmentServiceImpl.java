package com.farm2home.delivery.service.impl;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.kafka.DeliveryEventProducer;
import com.farm2home.delivery.mapper.DeliveryMapper;
import com.farm2home.delivery.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentServiceImpl implements DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryRouteRepository routeRepository;
    private final DeliveryMapper mapper;
    private final DeliveryEventProducer eventProducer;

    @Override
    @Transactional
    public AssignmentResponse manualAssign(ManualAssignRequest request) {
        DeliveryPartner partner = partnerRepository.findByIdAndDeletedFalse(request.getDeliveryPartnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found: " + request.getDeliveryPartnerId()));

        DeliveryRoute route = routeRepository.findByIdAndDeletedFalse(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + request.getRouteId()));

        if (assignmentRepository.existsByOrderId(request.getOrderId())) {
            throw new DeliveryException("Order " + request.getOrderId() + " already has a delivery assignment");
        }

        DeliveryAssignment assignment = assignmentRepository.save(DeliveryAssignment.builder()
                .orderId(request.getOrderId())
                .deliveryPartner(partner)
                .route(route)
                .build());

        eventProducer.publishDeliveryEvent(assignment, "DELIVERY_ASSIGNED");
        return mapper.toAssignmentResponse(assignment);
    }

    @Override
    @Transactional
    public AssignmentResponse updateStatus(UUID id, UpdateAssignmentStatusRequest request,
                                            UUID callerId, boolean isAdmin) {
        DeliveryAssignment assignment = resolve(id, callerId, isAdmin);

        AssignmentStatus current = assignment.getStatus();
        AssignmentStatus next    = request.getStatus();

        if (!current.canTransitionTo(next)) {
            throw new DeliveryException("Invalid status transition: " + current + " → " + next);
        }

        if (next == AssignmentStatus.FAILED) {
            if (request.getFailureReason() == null || request.getFailureReason().isBlank()) {
                throw new DeliveryException("Failure reason is required when marking as FAILED");
            }
            assignment.setFailureReason(request.getFailureReason());
        }

        if (next == AssignmentStatus.DELIVERED) {
            assignment.setDeliveredAt(LocalDateTime.now());
            assignment.setDeliveryProof(request.getDeliveryProof());
        }

        assignment.setStatus(next);
        DeliveryAssignment saved = assignmentRepository.save(assignment);

        String eventType = next == AssignmentStatus.DELIVERED ? "DELIVERY_COMPLETED" : "DELIVERY_" + next.name();
        eventProducer.publishDeliveryEvent(saved, eventType);

        return mapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse findById(UUID id, UUID callerId, boolean isAdmin) {
        return mapper.toAssignmentResponse(resolve(id, callerId, isAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> findAll(UUID partnerId, boolean isAdmin, Pageable pageable) {
        if (isAdmin) return assignmentRepository.findAll(pageable).map(mapper::toAssignmentResponse);
        return assignmentRepository.findAllByDeliveryPartnerId(partnerId, pageable)
                .map(mapper::toAssignmentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findByOrderId(UUID orderId) {
        return assignmentRepository.findAllByOrderId(orderId).stream()
                .map(mapper::toAssignmentResponse).toList();
    }

    private DeliveryAssignment resolve(UUID id, UUID callerId, boolean isAdmin) {
        if (isAdmin) {
            return assignmentRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
        }
        // Delivery partner: find partner record by userId, then check ownership
        DeliveryPartner partner = partnerRepository.findByUserIdAndDeletedFalse(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery partner profile for user: " + callerId));
        return assignmentRepository.findByIdAndDeliveryPartnerId(id, partner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
    }
}
