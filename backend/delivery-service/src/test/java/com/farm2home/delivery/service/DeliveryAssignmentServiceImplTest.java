package com.farm2home.delivery.service;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.DelayAssignmentRequest;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.client.PaymentServiceClient;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.common.core.analytics.DeliveryPerformancePoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
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
    @Mock private AuditLogService auditLogService;
    @Mock private PaymentServiceClient paymentServiceClient;

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
            verify(auditLogService).record(argThat(entry ->
                    entry.getAction().equals(com.farm2home.common.core.audit.AuditAction.ASSIGN)
                            && entry.getEntityId().equals(assignId.toString())));
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
            when(paymentServiceClient.hasPayableProgress(any())).thenReturn(Mono.just(true));
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
            verify(auditLogService).record(argThat(entry ->
                    "OUT_FOR_DELIVERY".equals(entry.getOldValue())
                            && "DELIVERED".equals(entry.getNewValue())
                            && entry.isSuccess()));
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
            when(paymentServiceClient.hasPayableProgress(any())).thenReturn(Mono.just(true));
            when(assignmentRepository.save(assignment)).thenReturn(assignment);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.OUT_FOR_DELIVERY));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            service.updateStatus(assignId, req, partnerUserId, false);

            verify(partnerRepository).findByUserIdAndDeletedFalse(partnerUserId);
            verify(assignmentRepository).findByIdAndDeliveryPartnerId(assignId, partnerId);
        }

        @Test
        @DisplayName("no payment initiated → throws DeliveryException, does not transition to OUT_FOR_DELIVERY")
        void noPaymentInitiated_throws() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));
            when(paymentServiceClient.hasPayableProgress(any())).thenReturn(Mono.just(false));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            assertThatThrownBy(() -> service.updateStatus(assignId, req, UUID.randomUUID(), true))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("payment");
            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("payment-service unreachable → throws DeliveryException rather than a raw error")
        void paymentServiceUnreachable_throwsDeliveryException() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));
            when(paymentServiceClient.hasPayableProgress(any())).thenReturn(Mono.error(
                    new org.springframework.web.reactive.function.client.WebClientRequestException(
                            new java.net.ConnectException("connection refused"),
                            org.springframework.http.HttpMethod.GET,
                            java.net.URI.create("http://payment-service/x"),
                            new org.springframework.http.HttpHeaders())));

            UpdateAssignmentStatusRequest req = new UpdateAssignmentStatusRequest();
            req.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            assertThatThrownBy(() -> service.updateStatus(assignId, req, UUID.randomUUID(), true))
                    .isInstanceOf(DeliveryException.class);
            verify(assignmentRepository, never()).save(any());
        }
    }

    // ── MarkDelayed ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("markDelayed()")
    class MarkDelayed {

        @Test
        @DisplayName("non-terminal assignment → publishes DELIVERY_DELAYED, status unchanged")
        void nonTerminal_publishesDelayEvent() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.OUT_FOR_DELIVERY));

            DelayAssignmentRequest req = new DelayAssignmentRequest();
            req.setReason("Heavy traffic on the route");

            AssignmentResponse result = service.markDelayed(assignId, req, UUID.randomUUID(), true);

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
            assertThat(result.getStatus()).isEqualTo("OUT_FOR_DELIVERY");
            verify(eventProducer).publishDeliveryEvent(assignment, "DELIVERY_DELAYED");
            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("terminal (DELIVERED) assignment → throws, no event published")
        void terminal_throws() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.DELIVERED);
            when(assignmentRepository.findById(assignId)).thenReturn(Optional.of(assignment));

            DelayAssignmentRequest req = new DelayAssignmentRequest();
            req.setReason("Heavy traffic on the route");

            assertThatThrownBy(() -> service.markDelayed(assignId, req, UUID.randomUUID(), true))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("DELIVERED");
            verify(eventProducer, never()).publishDeliveryEvent(any(), any());
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

    // ── GetSummary ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("returns completed deliveries count for today from repository")
        void returnsCompletedDeliveriesToday() {
            when(assignmentRepository.countByStatusAndDeliveredAtBetween(eq(AssignmentStatus.DELIVERED), any(), any()))
                    .thenReturn(7L);

            DeliverySummaryResponse result = service.getSummary();

            assertThat(result.getCompletedDeliveriesToday()).isEqualTo(7L);
            verify(assignmentRepository).countByStatusAndDeliveredAtBetween(eq(AssignmentStatus.DELIVERED), any(), any());
        }
    }

    // ── GetReport ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getReport()")
    class GetReport {

        @Test
        @DisplayName("maps the repository page into report rows and totals")
        void happyPath() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.DELIVERED);
            var page = new PageImpl<>(List.of(assignment), PageRequest.of(0, 20), 1);
            when(assignmentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(assignmentRepository.count(any(Specification.class))).thenReturn(1L, 0L);

            ReportPage<DeliveryReportRow, DeliveryReportSummary> result = service.getReport(
                    null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAssignmentId()).isEqualTo(assignId);
            assertThat(result.getContent().get(0).getOrderId()).isEqualTo(orderId);
            assertThat(result.getContent().get(0).getDeliveryPartnerId()).isEqualTo(partnerId);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("DELIVERED");
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getSummary().getTotalDeliveries()).isEqualTo(1);
            assertThat(result.getSummary().getCompletedCount()).isEqualTo(1L);
            assertThat(result.getSummary().getFailedCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("no matching assignments → empty content with zeroed summary")
        void noResults() {
            var page = new PageImpl<DeliveryAssignment>(List.of(), PageRequest.of(0, 20), 0);
            when(assignmentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(assignmentRepository.count(any(Specification.class))).thenReturn(0L, 0L);

            ReportPage<DeliveryReportRow, DeliveryReportSummary> result = service.getReport(
                    LocalDate.now().minusDays(7), LocalDate.now(), AssignmentStatus.FAILED, List.of(orderId),
                    PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalDeliveries()).isZero();
            assertThat(result.getSummary().getCompletedCount()).isZero();
            assertThat(result.getSummary().getFailedCount()).isZero();
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("admin: keyword + status + date range combine into one query, unscoped by partner")
        void allFiltersCombine_admin() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.DELIVERED);
            var page = new PageImpl<>(List.of(assignment), PageRequest.of(0, 20), 1);
            when(assignmentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.DELIVERED));

            Page<AssignmentResponse> result = service.search(null, true, "Ravi", LocalDate.now().minusDays(7),
                    LocalDate.now(), AssignmentStatus.DELIVERED, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(assignId);
        }

        @Test
        @DisplayName("non-admin with blank keyword → resolves caller's own DeliveryPartner id, no keyword predicate applied")
        void nonAdmin_scopedByPartner_blankKeyword() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            var page = new PageImpl<DeliveryAssignment>(List.of(), PageRequest.of(0, 20), 0);
            when(assignmentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            Page<AssignmentResponse> result = service.search(partnerUserId, false, "  ", null, null, null,
                    PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
            verify(partnerRepository).findByUserIdAndDeletedFalse(partnerUserId);
        }

        @Test
        @DisplayName("non-admin caller with no DeliveryPartner profile → throws ResourceNotFoundException")
        void nonAdmin_noPartnerProfile_throws() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.search(partnerUserId, false, null, null, null, null, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── FindAll ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("admin → sees every assignment, unscoped by partner")
        void admin_seesAll() {
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.DELIVERED);
            var page = new PageImpl<>(List.of(assignment), PageRequest.of(0, 20), 1);
            when(assignmentRepository.findAll(any(PageRequest.class))).thenReturn(page);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.DELIVERED));

            Page<AssignmentResponse> result = service.findAll(null, true, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(partnerRepository, never()).findByUserIdAndDeletedFalse(any());
        }

        @Test
        @DisplayName("non-admin → resolves caller's own DeliveryPartner id before filtering (not the raw caller id)")
        void nonAdmin_resolvesOwnPartnerId() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
            var page = new PageImpl<>(List.of(assignment), PageRequest.of(0, 20), 1);
            when(assignmentRepository.findAllByDeliveryPartnerId(partnerId, PageRequest.of(0, 20))).thenReturn(page);
            when(mapper.toAssignmentResponse(assignment)).thenReturn(buildResponse(AssignmentStatus.ASSIGNED));

            Page<AssignmentResponse> result = service.findAll(partnerUserId, false, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            // The key regression check: the repository is queried by the resolved DeliveryPartner.id
            // (partnerId), never by the caller's raw auth id (partnerUserId) - those are different UUIDs.
            verify(assignmentRepository).findAllByDeliveryPartnerId(partnerId, PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("non-admin caller with no DeliveryPartner profile → throws ResourceNotFoundException")
        void nonAdmin_noPartnerProfile_throws() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findAll(partnerUserId, false, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPerformanceTrend()")
    class GetPerformanceTrend {

        @Test
        @DisplayName("folds per-status rows for the same period into one point")
        void foldsRowsByPeriod() {
            DeliveryAssignmentRepository.DeliveryPerformanceRow delivered =
                    mock(DeliveryAssignmentRepository.DeliveryPerformanceRow.class);
            when(delivered.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(delivered.getStatus()).thenReturn("DELIVERED");
            when(delivered.getCnt()).thenReturn(3L);

            DeliveryAssignmentRepository.DeliveryPerformanceRow failed =
                    mock(DeliveryAssignmentRepository.DeliveryPerformanceRow.class);
            when(failed.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(failed.getStatus()).thenReturn("FAILED");
            when(failed.getCnt()).thenReturn(1L);

            when(assignmentRepository.findPerformanceTrend(eq("day"), any(), any()))
                    .thenReturn(List.of(delivered, failed));

            TrendSeries<DeliveryPerformancePoint> result = service.getPerformanceTrend(Granularity.DAILY, null, null);

            assertThat(result.getPoints()).hasSize(1);
            DeliveryPerformancePoint point = result.getPoints().get(0);
            assertThat(point.getTotalDeliveries()).isEqualTo(4);
            assertThat(point.getCompletedDeliveries()).isEqualTo(3);
            assertThat(point.getFailedDeliveries()).isEqualTo(1);
        }

        @Test
        @DisplayName("no rows → empty points list")
        void noRows_emptyPoints() {
            when(assignmentRepository.findPerformanceTrend(eq("year"), any(), any())).thenReturn(List.of());

            TrendSeries<DeliveryPerformancePoint> result = service.getPerformanceTrend(Granularity.YEARLY, null, null);

            assertThat(result.getPoints()).isEmpty();
        }
    }
}
