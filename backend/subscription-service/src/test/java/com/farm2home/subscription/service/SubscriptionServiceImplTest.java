package com.farm2home.subscription.service;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.SubscriptionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import com.farm2home.subscription.domain.repository.SubscriptionRepository;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.exception.ResourceNotFoundException;
import com.farm2home.subscription.exception.SubscriptionException;
import com.farm2home.subscription.kafka.SubscriptionEventProducer;
import com.farm2home.subscription.mapper.SubscriptionMapper;
import com.farm2home.subscription.service.impl.SubscriptionServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository repository;

    @Mock
    private SubscriptionMapper mapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private EntityManager entityManager;

    @Mock
    private SubscriptionEventProducer eventProducer;

    @InjectMocks
    private SubscriptionServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID subId      = UUID.randomUUID();

    private Subscription buildActive() {
        return Subscription.builder()
                .id(subId)
                .customerId(customerId)
                .milkType(MilkType.FULL_CREAM)
                .quantity(new BigDecimal("1.5"))
                .scheduleType(ScheduleType.DAILY)
                .startDate(LocalDate.now())
                .status(SubscriptionStatus.ACTIVE)
                .deleted(false)
                .build();
    }

    private SubscriptionResponse buildResponse(SubscriptionStatus status) {
        return SubscriptionResponse.builder()
                .id(subId)
                .customerId(customerId)
                .status(status.name())
                .build();
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid DAILY request → saves and returns response")
        void happyPath_daily() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(new BigDecimal("1.5"))
                    .scheduleType(ScheduleType.DAILY)
                    .startDate(LocalDate.now())
                    .build();

            Subscription saved = buildActive();
            SubscriptionResponse response = buildResponse(SubscriptionStatus.ACTIVE);

            when(mapper.toEntity(req)).thenReturn(new Subscription());
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(response);

            SubscriptionResponse result = service.create(req, customerId);

            assertThat(result.getId()).isEqualTo(subId);
            verify(repository).save(any(Subscription.class));
            verify(eventProducer).publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_CREATED, saved);
        }

        @Test
        @DisplayName("valid WEEKLY request with delivery days → saves successfully")
        void happyPath_weekly() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.TONED)
                    .quantity(new BigDecimal("2.0"))
                    .scheduleType(ScheduleType.WEEKLY)
                    .deliveryDays(List.of(
                            com.farm2home.subscription.domain.enums.DeliveryDay.MON,
                            com.farm2home.subscription.domain.enums.DeliveryDay.WED,
                            com.farm2home.subscription.domain.enums.DeliveryDay.FRI))
                    .startDate(LocalDate.now().plusDays(1))
                    .build();

            Subscription saved = buildActive();
            when(mapper.toEntity(req)).thenReturn(new Subscription());
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            SubscriptionResponse result = service.create(req, customerId);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("start date in past → throws SubscriptionException")
        void startDateInPast() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(new BigDecimal("1.0"))
                    .scheduleType(ScheduleType.DAILY)
                    .startDate(LocalDate.now().minusDays(1))
                    .build();

            assertThatThrownBy(() -> service.create(req, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("past");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("end date before start date → throws SubscriptionException")
        void endDateBeforeStartDate() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(new BigDecimal("1.0"))
                    .scheduleType(ScheduleType.DAILY)
                    .startDate(LocalDate.now().plusDays(5))
                    .endDate(LocalDate.now().plusDays(3))
                    .build();

            assertThatThrownBy(() -> service.create(req, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("End date");
        }

        @Test
        @DisplayName("WEEKLY schedule without delivery days → throws SubscriptionException")
        void weeklyWithoutDeliveryDays() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(new BigDecimal("1.0"))
                    .scheduleType(ScheduleType.WEEKLY)
                    .deliveryDays(Collections.emptyList())
                    .startDate(LocalDate.now())
                    .build();

            assertThatThrownBy(() -> service.create(req, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("delivery day");
        }

        @Test
        @DisplayName("customer already has an ACTIVE subscription of the same milk type → throws SubscriptionException")
        void duplicateActiveSubscription_sameMilkType_throws() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(new BigDecimal("1.0"))
                    .scheduleType(ScheduleType.DAILY)
                    .startDate(LocalDate.now())
                    .build();

            when(repository.existsByCustomerIdAndMilkTypeAndStatusAndDeletedFalse(
                    customerId, MilkType.FULL_CREAM, SubscriptionStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> service.create(req, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("already has an active");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("customer has an ACTIVE subscription of a different milk type → allowed")
        void activeSubscription_differentMilkType_allowed() {
            CreateSubscriptionRequest req = CreateSubscriptionRequest.builder()
                    .milkType(MilkType.TONED)
                    .quantity(new BigDecimal("1.0"))
                    .scheduleType(ScheduleType.DAILY)
                    .startDate(LocalDate.now())
                    .build();

            when(repository.existsByCustomerIdAndMilkTypeAndStatusAndDeletedFalse(
                    customerId, MilkType.TONED, SubscriptionStatus.ACTIVE)).thenReturn(false);
            when(mapper.toEntity(req)).thenReturn(new Subscription());
            when(repository.save(any())).thenReturn(buildActive());
            when(mapper.toResponse(any())).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            SubscriptionResponse result = service.create(req, customerId);

            assertThat(result).isNotNull();
            verify(repository).save(any());
        }
    }

    // ── findById ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing subscription for correct customer → returns response")
        void found() {
            Subscription sub = buildActive();
            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));
            when(mapper.toResponse(sub)).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            SubscriptionResponse result = service.findById(subId, customerId);
            assertThat(result.getId()).isEqualTo(subId);
        }

        @Test
        @DisplayName("not found or wrong customer → throws ResourceNotFoundException")
        void notFound() {
            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(subId, customerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("admin access (null customerId) → queries without customer filter")
        void adminAccess() {
            Subscription sub = buildActive();
            when(repository.findByIdAndDeletedFalse(subId)).thenReturn(Optional.of(sub));
            when(mapper.toResponse(sub)).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            SubscriptionResponse result = service.findById(subId, null);
            assertThat(result).isNotNull();
            verify(repository).findByIdAndDeletedFalse(subId);
            verify(repository, never()).findByIdAndCustomerIdAndDeletedFalse(any(), any());
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("update quantity on ACTIVE subscription → saves updated entity")
        void updateQuantity() {
            Subscription sub = buildActive();
            UpdateSubscriptionRequest req = UpdateSubscriptionRequest.builder()
                    .quantity(new BigDecimal("3.0"))
                    .build();

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));
            doAnswer(invocation -> {
                UpdateSubscriptionRequest r = invocation.getArgument(0);
                Subscription target = invocation.getArgument(1);
                if (r.getQuantity() != null) target.setQuantity(r.getQuantity());
                return null;
            }).when(mapper).updateEntityFromRequest(eq(req), eq(sub));
            when(repository.save(sub)).thenReturn(sub);
            when(mapper.toResponse(sub)).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            service.update(subId, req, customerId);

            assertThat(sub.getQuantity()).isEqualByComparingTo("3.0");
            verify(repository).save(sub);
        }

        @Test
        @DisplayName("update CANCELLED subscription → throws SubscriptionException")
        void updateCancelled() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.CANCELLED);

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.update(subId,
                    UpdateSubscriptionRequest.builder().build(), customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("cancelled");
        }

        @Test
        @DisplayName("change to WEEKLY without delivery days → throws SubscriptionException")
        void changeToWeeklyWithoutDays() {
            Subscription sub = buildActive();
            UpdateSubscriptionRequest req = UpdateSubscriptionRequest.builder()
                    .scheduleType(ScheduleType.WEEKLY)
                    .deliveryDays(null)
                    .build();

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.update(subId, req, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("Delivery days");
        }
    }

    // ── Cancel ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("ACTIVE → sets CANCELLED")
        void cancelsActive() {
            Subscription sub = buildActive();
            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));
            when(repository.save(sub)).thenReturn(sub);

            service.cancel(subId, customerId);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
            verify(auditLogService).record(argThat(entry ->
                    "ACTIVE".equals(entry.getOldValue()) && "CANCELLED".equals(entry.getNewValue())));
            verify(eventProducer).publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_CANCELLED, sub);
        }

        @Test
        @DisplayName("already CANCELLED → throws SubscriptionException")
        void alreadyCancelled() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.CANCELLED);

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.cancel(subId, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("Cannot transition")
                    .hasMessageContaining("CANCELLED");
            verifyNoInteractions(eventProducer);
        }
    }

    // ── Pause ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pause()")
    class Pause {

        @Test
        @DisplayName("ACTIVE → sets PAUSED with pause dates")
        void pausesActive() {
            Subscription sub = buildActive();
            PauseSubscriptionRequest req = new PauseSubscriptionRequest(LocalDate.now().plusDays(7));

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));
            when(repository.save(sub)).thenReturn(sub);
            when(mapper.toResponse(sub)).thenReturn(buildResponse(SubscriptionStatus.PAUSED));

            SubscriptionResponse result = service.pause(subId, req, customerId);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
            assertThat(sub.getPauseStart()).isEqualTo(LocalDate.now());
            assertThat(sub.getPauseEnd()).isEqualTo(req.getPauseEnd());
            assertThat(result.getStatus()).isEqualTo("PAUSED");
            verify(auditLogService).record(argThat(entry ->
                    "ACTIVE".equals(entry.getOldValue()) && "PAUSED".equals(entry.getNewValue())));
            verify(eventProducer).publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_PAUSED, sub);
        }

        @Test
        @DisplayName("already PAUSED → throws SubscriptionException")
        void alreadyPaused() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.PAUSED);

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.pause(subId,
                    new PauseSubscriptionRequest(LocalDate.now().plusDays(3)), customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("Cannot transition")
                    .hasMessageContaining("PAUSED");
        }

        @Test
        @DisplayName("CANCELLED → throws SubscriptionException")
        void pauseCancelled() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.CANCELLED);

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.pause(subId,
                    new PauseSubscriptionRequest(LocalDate.now().plusDays(3)), customerId))
                    .isInstanceOf(SubscriptionException.class);
        }
    }

    // ── Resume ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resume()")
    class Resume {

        @BeforeEach
        void stubPausedSub() {
        }

        @Test
        @DisplayName("PAUSED → sets ACTIVE and clears pause dates")
        void resumesPaused() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.PAUSED);
            sub.setPauseStart(LocalDate.now().minusDays(2));
            sub.setPauseEnd(LocalDate.now().plusDays(5));

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));
            when(repository.save(sub)).thenReturn(sub);
            when(mapper.toResponse(sub)).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            SubscriptionResponse result = service.resume(subId, customerId);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.getPauseStart()).isNull();
            assertThat(sub.getPauseEnd()).isNull();
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            verify(auditLogService).record(argThat(entry ->
                    "PAUSED".equals(entry.getOldValue()) && "ACTIVE".equals(entry.getNewValue())));
            verify(eventProducer).publish(EmailTemplateConstants.EVENT_SUBSCRIPTION_RESUMED, sub);
        }

        @Test
        @DisplayName("ACTIVE → throws SubscriptionException")
        void resumeActive() {
            Subscription sub = buildActive();

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.resume(subId, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("Cannot transition")
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("EXPIRED → throws SubscriptionException")
        void resumeExpired() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.EXPIRED);

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.resume(subId, customerId))
                    .isInstanceOf(SubscriptionException.class);
        }
    }

    // ── getSummary ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("returns active subscription count from repository")
        void happyPath() {
            when(repository.countByStatusAndDeletedFalse(SubscriptionStatus.ACTIVE)).thenReturn(7L);

            SubscriptionSummaryResponse result = service.getSummary();

            assertThat(result.getActiveSubscriptions()).isEqualTo(7L);
        }
    }

    // ── Scheduled jobs ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("expireEndedSubscriptions()")
    class ExpireEndedSubscriptions {

        @Test
        @DisplayName("delegates to repository's bulk expire query")
        void delegatesToRepository() {
            when(repository.expireByEndDate(any())).thenReturn(3);

            service.expireEndedSubscriptions();

            verify(repository).expireByEndDate(LocalDate.now());
        }

        @Test
        @DisplayName("zero expired → no error, still calls repository")
        void zeroExpired_noError() {
            when(repository.expireByEndDate(any())).thenReturn(0);

            service.expireEndedSubscriptions();

            verify(repository).expireByEndDate(any());
        }
    }

    @Nested
    @DisplayName("autoResumePausedSubscriptions()")
    class AutoResumePausedSubscriptions {

        @Test
        @DisplayName("PAUSED subscription whose pauseEnd has arrived → resumes it")
        void duePause_resumes() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.PAUSED);
            sub.setPauseStart(LocalDate.now().minusDays(3));
            sub.setPauseEnd(LocalDate.now());
            when(repository.findAllByDeletedFalse(any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(sub)));

            service.autoResumePausedSubscriptions();

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(sub.getPauseStart()).isNull();
            assertThat(sub.getPauseEnd()).isNull();
            verify(repository).save(sub);
        }

        @Test
        @DisplayName("PAUSED subscription whose pauseEnd is still in the future → left alone")
        void notYetDue_untouched() {
            Subscription sub = buildActive();
            sub.setStatus(SubscriptionStatus.PAUSED);
            sub.setPauseEnd(LocalDate.now().plusDays(5));
            when(repository.findAllByDeletedFalse(any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(sub)));

            service.autoResumePausedSubscriptions();

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("non-PAUSED subscription → left alone")
        void activeSubscription_untouched() {
            Subscription sub = buildActive();
            when(repository.findAllByDeletedFalse(any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(sub)));

            service.autoResumePausedSubscriptions();

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("checkUpcomingRenewals()")
    class CheckUpcomingRenewals {

        @Test
        @DisplayName("subscription ending within the warning window → logged, nothing saved")
        void expiringSoon_logged() {
            Subscription sub = buildActive();
            sub.setEndDate(LocalDate.now().plusDays(2));
            when(repository.findAllByStatusAndEndDateBetweenAndDeletedFalse(
                    eq(SubscriptionStatus.ACTIVE), any(), any())).thenReturn(List.of(sub));

            service.checkUpcomingRenewals();

            verify(repository).findAllByStatusAndEndDateBetweenAndDeletedFalse(
                    eq(SubscriptionStatus.ACTIVE), eq(LocalDate.now()), eq(LocalDate.now().plusDays(3)));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("nothing expiring soon → no error")
        void nothingExpiringSoon_noError() {
            when(repository.findAllByStatusAndEndDateBetweenAndDeletedFalse(
                    eq(SubscriptionStatus.ACTIVE), any(), any())).thenReturn(List.of());

            service.checkUpcomingRenewals();

            verify(repository, never()).save(any());
        }
    }

    // ── GetReport (Subscription Report) ─────────────────────────────────────────

    @Nested
    @DisplayName("getReport()")
    class GetReport {

        @Test
        @DisplayName("maps the repository page into report rows and totals")
        void happyPath() {
            Subscription sub = buildActive();
            var page = new PageImpl<>(List.of(sub), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(repository.count(any(Specification.class))).thenReturn(1L);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(new BigDecimal("1.5"));

            ReportPage<SubscriptionReportRow, SubscriptionReportSummary> result = service.getReport(
                    null, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSubscriptionId()).isEqualTo(subId);
            assertThat(result.getContent().get(0).getMilkType()).isEqualTo("FULL_CREAM");
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getSummary().getTotalSubscriptions()).isEqualTo(1);
            assertThat(result.getSummary().getActiveSubscriptions()).isEqualTo(1);
            assertThat(result.getSummary().getTotalQuantity()).isEqualByComparingTo("1.5");
        }

        @Test
        @DisplayName("no matching subscriptions → empty content with zeroed summary")
        void noResults() {
            var page = new PageImpl<Subscription>(List.of(), PageRequest.of(0, 20), 0);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(repository.count(any(Specification.class))).thenReturn(0L);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(BigDecimal.ZERO);

            ReportPage<SubscriptionReportRow, SubscriptionReportSummary> result = service.getReport(
                    LocalDate.now().minusDays(7), LocalDate.now(), SubscriptionStatus.CANCELLED, customerId, MilkType.TONED,
                    PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalSubscriptions()).isZero();
            assertThat(result.getSummary().getActiveSubscriptions()).isZero();
            assertThat(result.getSummary().getTotalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("keyword + customer + status + date range all combine into one query")
        void allFiltersCombine() {
            Subscription sub = buildActive();
            var page = new PageImpl<>(List.of(sub), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toResponse(sub)).thenReturn(buildResponse(SubscriptionStatus.ACTIVE));

            Page<SubscriptionResponse> result = service.search(customerId, "DAILY", LocalDate.now().minusDays(7),
                    LocalDate.now(), SubscriptionStatus.ACTIVE, MilkType.FULL_CREAM, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(subId);
        }

        @Test
        @DisplayName("blank keyword and null customerId → no keyword/customer predicate applied")
        void noOptionalFilters() {
            var page = new PageImpl<Subscription>(List.of(), PageRequest.of(0, 20), 0);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            Page<SubscriptionResponse> result = service.search(null, "  ", null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ── export ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("export()")
    class Export {

        @Test
        @DisplayName("CSV format → streams matching rows as CSV")
        void csv_streamsMatchingRows() throws Exception {
            Subscription sub = buildActive();
            var firstPage = new PageImpl<>(List.of(sub),
                    PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("startDate").ascending()), 1);
            var emptyPage = new PageImpl<>(List.<Subscription>of());
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(firstPage, emptyPage);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, customerId, "DAILY", null, null, null, null, "startDate", true);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).contains(subId.toString());
            assertThat(content).contains("FULL_CREAM");
        }

        @Test
        @DisplayName("no matches → writes header only")
        void noMatches_writesHeaderOnly() throws Exception {
            when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.<Subscription>of()));

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, null, null, null, null, null, null, "startDate", false);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8).trim())
                    .isEqualTo("Subscription ID,Customer ID,Milk Type,Quantity,Schedule Type,Start Date,End Date,Status");
        }
    }

    // ── getSubscriptionTrend ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSubscriptionTrend()")
    class GetSubscriptionTrend {

        @Test
        @DisplayName("maps already-aggregated repository rows into trend points")
        void mapsRows() {
            SubscriptionRepository.SubscriptionTrendRow row = mock(SubscriptionRepository.SubscriptionTrendRow.class);
            when(row.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(row.getNewSubscriptions()).thenReturn(5L);
            when(repository.findSubscriptionTrend(eq("day"), any(), any())).thenReturn(List.of(row));

            TrendSeries<SubscriptionTrendPoint> result = service.getSubscriptionTrend(
                    Granularity.DAILY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertThat(result.getPoints()).hasSize(1);
            assertThat(result.getPoints().get(0).getPeriod()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(result.getPoints().get(0).getNewSubscriptions()).isEqualTo(5L);
            assertThat(result.getGranularity()).isEqualTo(Granularity.DAILY);
        }

        @Test
        @DisplayName("no rows → empty points list")
        void noRows_emptyPoints() {
            when(repository.findSubscriptionTrend(eq("month"), any(), any())).thenReturn(List.of());

            assertThat(service.getSubscriptionTrend(Granularity.MONTHLY, null, null).getPoints()).isEmpty();
        }
    }
}
