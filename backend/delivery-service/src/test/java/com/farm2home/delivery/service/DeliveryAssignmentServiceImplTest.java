package com.farm2home.delivery.service;

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
import com.farm2home.delivery.service.impl.DeliveryAssignmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceImplTest {

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private DeliveryPartnerRepository partnerRepository;
    @Mock private DeliveryRouteRepository routeRepository;
    @Mock private DeliveryMapper mapper;
    @Mock private DeliveryEventProducer eventProducer;

    @InjectMocks private DeliveryAssignmentServiceImpl service;

    private final UUID orderId     = UUID.randomUUID();
    private final UUID partnerId   = UUID.randomUUID();
    private final UUID routeId     = UUID.randomUUID();
    private final UUID assignId    = UUID.randomUUID();
    private final UUID partnerUserId = UUID.randomUUID();

    private DeliveryRoute buildRoute() {
        return DeliveryRoute.builder().id(routeId).routeName("Route A")
                .routeCode("RTA").area("Banjara Hills").city("Hyderabad").pincode("500034").build();
    }

    private DeliveryPartner buildPartner() {
        return DeliveryPartner.builder().id(partnerId).userId(partnerUserId)
                .name("Ravi Kumar").mobile("9876543210").route(buildRoute()).build();
    }

    private DeliveryAssignment buildAssignment(AssignmentStatus status) {
        return DeliveryAssignment.builder()
                .id(assignId).orderId(orderId)
                .deliveryPartner(buildPartner()).route(buildRoute())
                .status(status).build();
    }

    private AssignmentResponse buildResponse(AssignmentStatus status) {
        return AssignmentResponse.builder().id(assignId).orderId(orderId)
                .status(status.name()).build();
    }

    // ── ManualAssign ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("manualAssign()")
    class ManualAssign {

        @Test
        @DisplayName("valid request → creates assignment and publishes DELIVERY_ASSIGNED event")
        void happyPath() {
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.of(buildPartner()));
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(buildRoute()));
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(false);
            DeliveryAssignment saved = buildAssignment(AssignmentStatus.ASSIGNED);
            when(assignmentRepository.save(any())).thenReturn(saved);
            when(mapper.toAssignmentResponse(saved)).thenReturn(buildResponse(AssignmentStatus.ASSIGNED));

            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(orderId);
            req.setDeliveryPartnerId(partnerId);
            req.setRouteId(routeId);

            AssignmentResponse result = service.manualAssign(req);

            assertThat(result.getStatus()).isEqualTo("ASSIGNED");
            verify(eventProducer).publishDeliveryEvent(saved, "DELIVERY_ASSIGNED");
        }

        @Test
        @DisplayName("order already assigned → throws DeliveryException")
        void duplicateAssignment_throws() {
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.of(buildPartner()));
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(buildRoute()));
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(true);

            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(orderId);
            req.setDeliveryPartnerId(partnerId);
            req.setRouteId(routeId);

            assertThatThrownBy(() -> service.manualAssign(req))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("already has a delivery assignment");
            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("partner not found → throws ResourceNotFoundException")
        void partnerNotFound_throws() {
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.empty());

            ManualAssignRequest req = new ManualAssignRequest();
            req.setOrderId(orderId);
            req.setDeliveryPartnerId(partnerId);
            req.setRouteId(routeId);

            assertThatThrownBy(() -> service.manualAssign(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── UpdateStatus ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("ASSIGNED → OUT_FOR_DELIVERY is valid (admin)")
        void assignedToOutForDelivery() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));
            when(assignmentRepository.save(assignment)).thenReturn(assignment);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.OUT_FOR_DELIVERY));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            AssignmentResponse result = service.updateStatus(assignId, req, UUID.randomUUID(), true);

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
            verify(eventProducer).publishDeliveryEvent(assignment, "DELIVERY_OUT_FOR_DELIVERY");
        }

        @Test
        @DisplayName("OUT_FOR_DELIVERY → DELIVERED sets deliveredAt and publishes DELIVERY_COMPLETED")
        void outForDeliveryToDelivered() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));
            when(assignmentRepository.save(assignment)).thenReturn(assignment);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.DELIVERED));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.DELIVERED);
            req.setDeliveryProof("https://cdn.farm2home.in/proofs/photo123.jpg");

            service.updateStatus(assignId, req, UUID.randomUUID(), true);

            assertThat(assignment.getDeliveredAt()).isNotNull();
            assertThat(assignment.getDeliveryProof()).isEqualTo("https://cdn.farm2home.in/proofs/photo123.jpg");
            verify(eventProducer).publishDeliveryEvent(assignment, "DELIVERY_COMPLETED");
        }

        @Test
        @DisplayName("ASSIGNED → FAILED requires failure reason")
        void failedRequiresReason() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.FAILED);
            // no failure reason

            assertThatThrownBy(() -> service.updateStatus(assignId, req, UUID.randomUUID(), true))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("Failure reason is required");
        }

        @Test
        @DisplayName("DELIVERED → FAILED is an invalid transition (terminal status)")
        void fromTerminal_throws() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.DELIVERED);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.FAILED);

            assertThatThrownBy(() -> service.updateStatus(assignId, req, UUID.randomUUID(), true))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("Invalid status transition");
        }

        @Test
        @DisplayName("delivery partner accesses own assignment via userId lookup")
        void partnerOwnershipCheck() {
            DeliveryPartner partner = buildPartner();
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(partner));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignId, partnerId))
                    .thenReturn(Optional.of(assignment));
            when(assignmentRepository.save(assignment)).thenReturn(assignment);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.OUT_FOR_DELIVERY));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            service.updateStatus(assignId, req, partnerUserId, false);

            verify(partnerRepository).findByUserIdAndDeletedFalse(partnerUserId);
            verify(assignmentRepository).findByIdAndDeliveryPartnerId(assignId, partnerId);
        }
    }

    // ── AssignmentStatus state machine ───────────────────────────────────────────

    @Nested
    @DisplayName("AssignmentStatus.canTransitionTo()")
    class StateMachine {

        @Test
        void assigned_toOutForDelivery_valid() {
            assertThat(AssignmentStatus.ASSIGNED.canTransitionTo(AssignmentStatus.OUT_FOR_DELIVERY)).isTrue();
        }

        @Test
        void assigned_toFailed_valid() {
            assertThat(AssignmentStatus.ASSIGNED.canTransitionTo(AssignmentStatus.FAILED)).isTrue();
        }

        @Test
        void assigned_toDelivered_invalid() {
            assertThat(AssignmentStatus.ASSIGNED.canTransitionTo(AssignmentStatus.DELIVERED)).isFalse();
        }

        @Test
        void outForDelivery_toDelivered_valid() {
            assertThat(AssignmentStatus.OUT_FOR_DELIVERY.canTransitionTo(AssignmentStatus.DELIVERED)).isTrue();
        }

        @Test
        void delivered_toAnything_invalid() {
            assertThat(AssignmentStatus.DELIVERED.canTransitionTo(AssignmentStatus.FAILED)).isFalse();
            assertThat(AssignmentStatus.DELIVERED.canTransitionTo(AssignmentStatus.ASSIGNED)).isFalse();
        }
    }
}
