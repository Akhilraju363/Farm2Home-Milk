package com.farm2home.subscription.service.impl;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.SubscriptionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.export.TabularExporterFactory;
import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.kafka.SubscriptionEventProducer;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import com.farm2home.subscription.domain.repository.SubscriptionRepository;
import com.farm2home.subscription.domain.repository.SubscriptionSpecifications;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.exception.ResourceNotFoundException;
import com.farm2home.subscription.exception.SubscriptionException;
import com.farm2home.subscription.mapper.SubscriptionMapper;
import com.farm2home.subscription.service.SubscriptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final int RENEWAL_WARNING_WINDOW_DAYS = 3;

    private final SubscriptionRepository repository;
    private final SubscriptionMapper mapper;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final SubscriptionEventProducer eventProducer;

    @Override
    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "Subscription")
    public SubscriptionResponse create(CreateSubscriptionRequest request, UUID customerId) {
        validateCreateRequest(request);

        if (repository.existsByCustomerIdAndMilkTypeAndStatusAndDeletedFalse(
                customerId, request.getMilkType(), SubscriptionStatus.ACTIVE)) {
            throw new SubscriptionException(
                    "Customer already has an active " + request.getMilkType().name() + " subscription.");
        }

        Subscription subscription = mapper.toEntity(request);
        subscription.setCustomerId(customerId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        Subscription saved = repository.save(subscription);
        log.info("Created subscription {} for customer {}", saved.getId(), customerId);
        eventProducer.publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_CREATED, saved);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> findAll(UUID customerId, Pageable pageable) {
        if (customerId == null) {
            return repository.findAllByDeletedFalse(pageable).map(mapper::toResponse);
        }
        return repository.findAllByCustomerIdAndDeletedFalse(customerId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse findById(UUID id, UUID customerId) {
        return mapper.toResponse(findSubscription(id, customerId));
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Subscription")
    public SubscriptionResponse update(UUID id, UpdateSubscriptionRequest request, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);

        if (sub.getStatus().isTerminal()) {
            throw new SubscriptionException("Cannot modify a " + sub.getStatus().name().toLowerCase() + " subscription.");
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(sub.getStartDate())) {
            throw new SubscriptionException("End date must be after the subscription start date.");
        }
        if (request.getScheduleType() == ScheduleType.WEEKLY
                && (request.getDeliveryDays() == null || request.getDeliveryDays().isEmpty())) {
            throw new SubscriptionException("Delivery days are required for a WEEKLY schedule.");
        }

        mapper.updateEntityFromRequest(request, sub);

        return mapper.toResponse(repository.save(sub));
    }

    @Override
    @Transactional
    public void cancel(UUID id, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);
        requireTransition(sub.getStatus(), SubscriptionStatus.CANCELLED);

        SubscriptionStatus previousStatus = sub.getStatus();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        Subscription saved = repository.save(sub);
        log.info("Cancelled subscription {}", id);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("Subscription")
                .entityId(id.toString())
                .oldValue(previousStatus.name())
                .newValue(SubscriptionStatus.CANCELLED.name())
                .details("Subscription cancelled")
                .build());
        eventProducer.publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_CANCELLED, saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse pause(UUID id, PauseSubscriptionRequest request, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);
        requireTransition(sub.getStatus(), SubscriptionStatus.PAUSED);

        if (!request.getPauseEnd().isAfter(LocalDate.now())) {
            throw new SubscriptionException("Pause end date must be a future date.");
        }

        SubscriptionStatus previousStatus = sub.getStatus();
        sub.setStatus(SubscriptionStatus.PAUSED);
        sub.setPauseStart(LocalDate.now());
        sub.setPauseEnd(request.getPauseEnd());

        Subscription saved = repository.save(sub);
        log.info("Paused subscription {} until {}", id, request.getPauseEnd());
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("Subscription")
                .entityId(saved.getId().toString())
                .oldValue(previousStatus.name())
                .newValue(SubscriptionStatus.PAUSED.name())
                .details("Subscription paused until " + request.getPauseEnd())
                .build());
        eventProducer.publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_PAUSED, saved);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse resume(UUID id, UUID customerId) {
        Subscription sub = findSubscription(id, customerId);
        requireTransition(sub.getStatus(), SubscriptionStatus.ACTIVE);

        SubscriptionStatus previousStatus = sub.getStatus();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPauseStart(null);
        sub.setPauseEnd(null);

        Subscription saved = repository.save(sub);
        log.info("Resumed subscription {}", id);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("Subscription")
                .entityId(saved.getId().toString())
                .oldValue(previousStatus.name())
                .newValue(SubscriptionStatus.ACTIVE.name())
                .details("Subscription resumed")
                .build());
        eventProducer.publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_RESUMED, saved);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionSummaryResponse getSummary() {
        return SubscriptionSummaryResponse.builder()
                .activeSubscriptions(repository.countByStatusAndDeletedFalse(SubscriptionStatus.ACTIVE))
                .build();
    }

    // ── Scheduled jobs ──────────────────────────────────────────────────────────

    /** Daily at 01:00 — expire subscriptions whose end_date has passed. */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void expireEndedSubscriptions() {
        int count = repository.expireByEndDate(LocalDate.now());
        if (count > 0) {
            log.info("Expired {} subscription(s) past their end date.", count);
        }
    }

    /** Daily at 01:05 — auto-resume subscriptions whose pause_end has arrived. */
    @Scheduled(cron = "0 5 1 * * *")
    @Transactional
    public void autoResumePausedSubscriptions() {
        repository.findAllByDeletedFalse(Pageable.unpaged()).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.PAUSED
                        && s.getPauseEnd() != null
                        && !LocalDate.now().isBefore(s.getPauseEnd()))
                .forEach(s -> {
                    s.setStatus(SubscriptionStatus.ACTIVE);
                    s.setPauseStart(null);
                    s.setPauseEnd(null);
                    repository.save(s);
                    log.info("Auto-resumed subscription {}", s.getId());
                });
    }

    /** Daily at 01:10 — flag ACTIVE subscriptions ending within the next few days.
     *  No auto-renew concept exists in the data model (endDate is just a hard stop that
     *  expireEndedSubscriptions() enforces), so this stays ops visibility only (log-based), not
     *  a customer-facing alert - deliberately kept distinct from the four lifecycle events
     *  eventProducer.publish() sends (CREATED/PAUSED/RESUMED/CANCELLED), which represent actions
     *  that already happened rather than a forward-looking warning. */
    @Scheduled(cron = "0 10 1 * * *")
    @Transactional(readOnly = true)
    public void checkUpcomingRenewals() {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(RENEWAL_WARNING_WINDOW_DAYS);

        List<Subscription> expiringSoon = repository.findAllByStatusAndEndDateBetweenAndDeletedFalse(
                SubscriptionStatus.ACTIVE, today, windowEnd);

        for (Subscription sub : expiringSoon) {
            log.warn("Subscription {} (customer {}) ends on {} - within the {}-day renewal window.",
                    sub.getId(), sub.getCustomerId(), sub.getEndDate(), RENEWAL_WARNING_WINDOW_DAYS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPage<SubscriptionReportRow, SubscriptionReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            SubscriptionStatus status, UUID customerId, MilkType milkType, Pageable pageable) {
        Specification<Subscription> baseSpec = Specification.where(SubscriptionSpecifications.notDeleted());
        if (dateFrom != null || dateTo != null) {
            baseSpec = baseSpec.and(SubscriptionSpecifications.startDateBetween(dateFrom, dateTo));
        }
        if (customerId != null) {
            baseSpec = baseSpec.and(SubscriptionSpecifications.hasCustomer(customerId));
        }
        if (milkType != null) {
            baseSpec = baseSpec.and(SubscriptionSpecifications.hasMilkType(milkType));
        }
        Specification<Subscription> spec = status != null ? baseSpec.and(SubscriptionSpecifications.hasStatus(status)) : baseSpec;

        Page<Subscription> page = repository.findAll(spec, pageable);
        List<SubscriptionReportRow> rows = page.getContent().stream()
                .map(s -> SubscriptionReportRow.builder()
                        .subscriptionId(s.getId())
                        .customerId(s.getCustomerId())
                        .milkType(s.getMilkType().name())
                        .quantity(s.getQuantity())
                        .scheduleType(s.getScheduleType().name())
                        .startDate(s.getStartDate())
                        .endDate(s.getEndDate())
                        .status(s.getStatus().name())
                        .build())
                .toList();

        SubscriptionReportSummary summary = SubscriptionReportSummary.builder()
                .totalSubscriptions(page.getTotalElements())
                .activeSubscriptions(repository.count(baseSpec.and(SubscriptionSpecifications.hasStatus(SubscriptionStatus.ACTIVE))))
                .totalQuantity(sumQuantity(spec))
                .build();

        return ReportPage.<SubscriptionReportRow, SubscriptionReportSummary>builder()
                .content(rows)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .summary(summary)
                .build();
    }

    /** Reuses the same Specification that builds the page's WHERE clause, so the aggregate
     *  total is always computed over exactly the same filtered set - just a different SELECT. */
    private BigDecimal sumQuantity(Specification<Subscription> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<Subscription> root = cq.from(Subscription.class);
        cq.select(cb.coalesce(cb.sum(root.get("quantity")), BigDecimal.ZERO));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> search(UUID customerId, String keyword, LocalDate dateFrom, LocalDate dateTo,
            SubscriptionStatus status, MilkType milkType, Pageable pageable) {
        Specification<Subscription> spec = buildSearchSpecification(customerId, keyword, dateFrom, dateTo, status, milkType);
        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    /** Shared by both {@link #search} and {@link #export} so the two always see the exact same
     *  filtered result set. */
    private Specification<Subscription> buildSearchSpecification(UUID customerId, String keyword, LocalDate dateFrom,
            LocalDate dateTo, SubscriptionStatus status, MilkType milkType) {
        Specification<Subscription> spec = Specification.where(SubscriptionSpecifications.notDeleted());
        if (customerId != null) {
            spec = spec.and(SubscriptionSpecifications.hasCustomer(customerId));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(SubscriptionSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(SubscriptionSpecifications.startDateBetween(dateFrom, dateTo));
        }
        if (status != null) {
            spec = spec.and(SubscriptionSpecifications.hasStatus(status));
        }
        if (milkType != null) {
            spec = spec.and(SubscriptionSpecifications.hasMilkType(milkType));
        }
        return spec;
    }

    /** Streams matching subscriptions straight to {@code out} in the requested file format, one
     *  bounded page at a time, so exporting a very large subscription base never requires holding
     *  the full result set in memory. Each batch fetch runs in its own short-lived Spring Data
     *  transaction (this method is deliberately NOT wrapped in a single @Transactional so a
     *  slow export doesn't pin one DB connection for its entire duration). Runs on the async
     *  StreamingResponseBody dispatch thread, not the original request thread. */
    @Override
    public void export(ExportFormat format, OutputStream out, UUID customerId, String keyword, LocalDate dateFrom,
            LocalDate dateTo, SubscriptionStatus status, MilkType milkType, String sortBy, boolean ascending) throws IOException {
        Specification<Subscription> spec = buildSearchSpecification(customerId, keyword, dateFrom, dateTo, status, milkType);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        List<ExportColumn<Subscription>> columns = List.of(
                new ExportColumn<>("Subscription ID", s -> s.getId().toString()),
                new ExportColumn<>("Customer ID", s -> s.getCustomerId().toString()),
                new ExportColumn<>("Milk Type", s -> s.getMilkType().name()),
                new ExportColumn<>("Quantity", s -> s.getQuantity().toString()),
                new ExportColumn<>("Schedule Type", s -> s.getScheduleType().name()),
                new ExportColumn<>("Start Date", s -> s.getStartDate().toString()),
                new ExportColumn<>("End Date", s -> s.getEndDate() == null ? "" : s.getEndDate().toString()),
                new ExportColumn<>("Status", s -> s.getStatus().name()));

        BatchSupplier<Subscription> supplier = (page, size) ->
                repository.findAll(spec, PageRequest.of(page, size, sort)).getContent();

        TabularExporterFactory.<Subscription>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public TrendSeries<SubscriptionTrendPoint> getSubscriptionTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo) {
        List<SubscriptionTrendPoint> points = repository.findSubscriptionTrend(granularity.getSqlUnit(), dateFrom, dateTo)
                .stream()
                .map(row -> SubscriptionTrendPoint.builder().period(row.getPeriod()).newSubscriptions(row.getNewSubscriptions()).build())
                .toList();

        return TrendSeries.<SubscriptionTrendPoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(points).build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Single source of truth for which subscription status transitions are legal - reused by
     *  cancel()/pause()/resume() so the rules live in one place (SubscriptionStatus.canTransitionTo)
     *  instead of being re-derived as ad-hoc conditionals in each method. */
    private void requireTransition(SubscriptionStatus current, SubscriptionStatus next) {
        if (!current.canTransitionTo(next)) {
            throw new SubscriptionException(
                    "Cannot transition subscription from " + current.name() + " to " + next.name() + ".");
        }
    }

    private Subscription findSubscription(UUID id, UUID customerId) {
        if (customerId == null) {
            return repository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
        }
        return repository.findByIdAndCustomerIdAndDeletedFalse(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found or access denied: " + id));
    }

    private void validateCreateRequest(CreateSubscriptionRequest request) {
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new SubscriptionException("Start date cannot be in the past.");
        }
        if (request.getEndDate() != null && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new SubscriptionException("End date must be after the start date.");
        }
        if (request.getScheduleType() == ScheduleType.WEEKLY
                && (request.getDeliveryDays() == null || request.getDeliveryDays().isEmpty())) {
            throw new SubscriptionException("At least one delivery day is required for a WEEKLY schedule.");
        }
    }
}
