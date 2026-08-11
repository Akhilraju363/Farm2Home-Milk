package com.farm2home.delivery.service.impl;

import com.farm2home.common.core.analytics.DeliveryPerformancePoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.delivery.client.OrderDetailResponse;
import com.farm2home.delivery.client.OrderServiceClient;
import com.farm2home.delivery.client.PaymentServiceClient;
import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentSpecifications;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.DelayAssignmentRequest;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.kafka.DeliveryEventProducer;
import com.farm2home.delivery.mapper.DeliveryMapper;
import com.farm2home.delivery.service.DeliveryAssignmentService;
import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentServiceImpl implements DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryRouteRepository routeRepository;
    private final DeliveryMapper mapper;
    private final DeliveryEventProducer eventProducer;
    private final AuditLogService auditLogService;
    private final PaymentServiceClient paymentServiceClient;
    private final OrderServiceClient orderServiceClient;

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

        // ManualAssignRequest carries no customer/order-number info (unlike the ORDER_CREATED
        // Kafka event OrderEventConsumer auto-assigns from), so a manually-created assignment
        // used to leave customerId/orderNumber null - harmless for the fields that already
        // existed, but it silently broke any future feature needing to verify a CUSTOMER owns
        // this assignment (delivery-tracking's customer-ownership check needs exactly that).
        // Backfilling from order-service here keeps both assignment paths consistent.
        OrderDetailResponse order = orderServiceClient.getOrder(request.getOrderId())
                .onErrorResume(ex -> Mono.empty())
                .block();

        DeliveryAssignment assignment = assignmentRepository.save(DeliveryAssignment.builder()
                .orderId(request.getOrderId())
                .customerId(order != null ? order.getCustomerId() : null)
                .orderNumber(order != null ? order.getOrderNumber() : null)
                .deliveryPartner(partner)
                .route(route)
                .build());

        eventProducer.publishDeliveryEvent(assignment, EmailTemplateConstants.EVENT_DELIVERY_ASSIGNED);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.ASSIGN)
                .entityType("DeliveryAssignment")
                .entityId(assignment.getId().toString())
                .details("Assigned to delivery partner " + request.getDeliveryPartnerId())
                .build());
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

        if (next == AssignmentStatus.OUT_FOR_DELIVERY && !hasPayableProgress(assignment.getOrderId())) {
            throw new DeliveryException(
                    "Cannot dispatch order " + assignment.getOrderId() + " for delivery: no payment has been initiated yet.");
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

        String eventType = next == AssignmentStatus.DELIVERED
                ? EmailTemplateConstants.EVENT_DELIVERY_COMPLETED : "DELIVERY_" + next.name();
        eventProducer.publishDeliveryEvent(saved, eventType);

        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.DELIVERY)
                .entityType("DeliveryAssignment")
                .entityId(saved.getId().toString())
                .oldValue(current.name())
                .newValue(next.name())
                .success(next != AssignmentStatus.FAILED)
                .failureReason(next == AssignmentStatus.FAILED ? request.getFailureReason() : null)
                .build());

        return mapper.toAssignmentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse markDelayed(UUID id, DelayAssignmentRequest request, UUID callerId, boolean isAdmin) {
        DeliveryAssignment assignment = resolve(id, callerId, isAdmin);

        if (assignment.getStatus().isTerminal()) {
            throw new DeliveryException(
                    "Cannot mark a " + assignment.getStatus() + " assignment as delayed");
        }

        // Notification-only signal - status/persisted state is unchanged. readOnly=true
        // disables Hibernate's dirty-checking auto-flush, so setting failureReason here
        // just long enough for the outgoing event to read it does NOT get persisted.
        assignment.setFailureReason(request.getReason());
        eventProducer.publishDeliveryEvent(assignment, EmailTemplateConstants.EVENT_DELIVERY_DELAYED);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.DELIVERY)
                .entityType("DeliveryAssignment")
                .entityId(assignment.getId().toString())
                .details("Delivery delayed: " + request.getReason())
                .build());

        return mapper.toAssignmentResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse findById(UUID id, UUID callerId, boolean isAdmin) {
        return mapper.toAssignmentResponse(resolve(id, callerId, isAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> findAll(UUID callerId, boolean isAdmin, Pageable pageable) {
        if (isAdmin) return assignmentRepository.findAll(pageable).map(mapper::toAssignmentResponse);
        return assignmentRepository.findAllByDeliveryPartnerId(resolvePartnerId(callerId), pageable)
                .map(mapper::toAssignmentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findByOrderId(UUID orderId, UUID callerId, boolean isAdmin) {
        List<DeliveryAssignment> assignments = assignmentRepository.findAllByOrderId(orderId);

        if (isAdmin) {
            return assignments.stream().map(mapper::toAssignmentResponse).toList();
        }

        // A caller who resolves to a DeliveryPartner profile only ever sees their own
        // assignments; a caller who doesn't (a CUSTOMER, or a DELIVERY_PARTNER with no profile
        // at all) falls through to the customer-ownership check below - assignment.customerId is
        // now reliably populated for both auto- and manually-created assignments (see
        // manualAssign()), which is what makes this branch safe: it was deliberately absent
        // before that fix, since delivery-service previously had no reliable way to verify order
        // ownership on its own (see the historical Javadoc on the controller's GET /order/{id}).
        Optional<UUID> callerPartnerId = partnerRepository.findByUserIdAndDeletedFalse(callerId).map(DeliveryPartner::getId);
        if (callerPartnerId.isPresent()) {
            return assignments.stream()
                    .filter(a -> callerPartnerId.get().equals(a.getDeliveryPartner().getId()))
                    .map(mapper::toAssignmentResponse)
                    .toList();
        }

        return assignments.stream()
                .filter(a -> callerId.equals(a.getCustomerId()))
                .map(mapper::toAssignmentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliverySummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = today.plusDays(1).atStartOfDay();
        return DeliverySummaryResponse.builder()
                .completedDeliveriesToday(assignmentRepository.countByStatusAndDeliveredAtBetween(
                        AssignmentStatus.DELIVERED, startOfDay, startOfNextDay))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPage<DeliveryReportRow, DeliveryReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            AssignmentStatus status, List<UUID> orderIds, Pageable pageable) {
        Specification<DeliveryAssignment> dateSpec = Specification.where(null);
        if (dateFrom != null || dateTo != null) {
            dateSpec = dateSpec.and(DeliveryAssignmentSpecifications.assignedBetween(dateFrom, dateTo));
        }
        if (orderIds != null && !orderIds.isEmpty()) {
            dateSpec = dateSpec.and(DeliveryAssignmentSpecifications.hasOrderIdIn(orderIds));
        }
        Specification<DeliveryAssignment> spec = status != null
                ? dateSpec.and(DeliveryAssignmentSpecifications.hasStatus(status)) : dateSpec;

        Page<DeliveryAssignment> page = assignmentRepository.findAll(spec, pageable);
        List<DeliveryReportRow> rows = page.getContent().stream()
                .map(a -> DeliveryReportRow.builder()
                        .assignmentId(a.getId())
                        .orderId(a.getOrderId())
                        .deliveryPartnerId(a.getDeliveryPartner() != null ? a.getDeliveryPartner().getId() : null)
                        .status(a.getStatus().name())
                        .assignedAt(a.getAssignedAt())
                        .deliveredAt(a.getDeliveredAt())
                        .build())
                .toList();

        DeliveryReportSummary summary = DeliveryReportSummary.builder()
                .totalDeliveries(page.getTotalElements())
                .completedCount(assignmentRepository.count(
                        dateSpec.and(DeliveryAssignmentSpecifications.hasStatus(AssignmentStatus.DELIVERED))))
                .failedCount(assignmentRepository.count(
                        dateSpec.and(DeliveryAssignmentSpecifications.hasStatus(AssignmentStatus.FAILED))))
                .build();

        return ReportPage.<DeliveryReportRow, DeliveryReportSummary>builder()
                .content(rows)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .summary(summary)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> search(UUID callerId, boolean isAdmin, String keyword, LocalDate dateFrom,
            LocalDate dateTo, AssignmentStatus status, Pageable pageable) {
        Specification<DeliveryAssignment> spec = Specification.where(null);
        if (!isAdmin) {
            spec = spec.and(DeliveryAssignmentSpecifications.hasDeliveryPartner(resolvePartnerId(callerId)));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(DeliveryAssignmentSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(DeliveryAssignmentSpecifications.assignedBetween(dateFrom, dateTo));
        }
        if (status != null) {
            spec = spec.and(DeliveryAssignmentSpecifications.hasStatus(status));
        }
        return assignmentRepository.findAll(spec, pageable).map(mapper::toAssignmentResponse);
    }

    /** DeliveryAssignmentRepository.findPerformanceTrend does the real aggregation (COUNT GROUP
     *  BY period + status) in Postgres; this method only folds the handful of per-status rows
     *  for each period into one point - a linear scan over an already-tiny, already-aggregated
     *  result set, not a re-aggregation of raw assignments. */
    @Override
    @Transactional(readOnly = true)
    public TrendSeries<DeliveryPerformancePoint> getPerformanceTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime start = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;

        Map<LocalDate, DeliveryPerformancePoint> byPeriod = new LinkedHashMap<>();
        for (DeliveryAssignmentRepository.DeliveryPerformanceRow row : assignmentRepository.findPerformanceTrend(
                granularity.getSqlUnit(), start, endExclusive)) {
            DeliveryPerformancePoint point = byPeriod.computeIfAbsent(row.getPeriod(), period -> DeliveryPerformancePoint.builder()
                    .period(period).totalDeliveries(0).completedDeliveries(0).failedDeliveries(0).build());
            point.setTotalDeliveries(point.getTotalDeliveries() + row.getCnt());
            if (AssignmentStatus.DELIVERED.name().equals(row.getStatus())) {
                point.setCompletedDeliveries(point.getCompletedDeliveries() + row.getCnt());
            } else if (AssignmentStatus.FAILED.name().equals(row.getStatus())) {
                point.setFailedDeliveries(point.getFailedDeliveries() + row.getCnt());
            }
        }

        return TrendSeries.<DeliveryPerformancePoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(List.copyOf(byPeriod.values())).build();
    }

    private boolean hasPayableProgress(UUID orderId) {
        try {
            Boolean result = paymentServiceClient.hasPayableProgress(orderId).block();
            return Boolean.TRUE.equals(result);
        } catch (org.springframework.web.reactive.function.client.WebClientException ex) {
            throw new DeliveryException("Could not verify payment status right now. Please try again.");
        }
    }

    private DeliveryAssignment resolve(UUID id, UUID callerId, boolean isAdmin) {
        if (isAdmin) {
            return assignmentRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
        }
        return assignmentRepository.findByIdAndDeliveryPartnerId(id, resolvePartnerId(callerId))
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
    }

    /** Translates the caller's own auth user id to their DeliveryPartner.id - the two are
     *  distinct UUIDs (DeliveryPartner has its own generated id, referencing userId as a foreign
     *  key), so every non-admin query scoped "to the caller's own deliveries" must resolve
     *  through this, not use callerId directly as if it were a DeliveryPartner id. */
    private UUID resolvePartnerId(UUID callerId) {
        return partnerRepository.findByUserIdAndDeletedFalse(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery partner profile for user: " + callerId))
                .getId();
    }
}
