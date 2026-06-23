package com.farm2home.subscription.service;

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
import com.farm2home.subscription.mapper.SubscriptionMapper;
import com.farm2home.subscription.service.impl.SubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository repository;

    @Mock
    private SubscriptionMapper mapper;

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

            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(response);

            SubscriptionResponse result = service.create(req, customerId);

            assertThat(result.getId()).isEqualTo(subId);
            verify(repository).save(any(Subscription.class));
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
                    .hasMessageContaining("delivery day");
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
                    .hasMessageContaining("already cancelled");
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
                    .hasMessageContaining("ACTIVE");
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
        }

        @Test
        @DisplayName("ACTIVE → throws SubscriptionException")
        void resumeActive() {
            Subscription sub = buildActive();

            when(repository.findByIdAndCustomerIdAndDeletedFalse(subId, customerId))
                    .thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.resume(subId, customerId))
                    .isInstanceOf(SubscriptionException.class)
                    .hasMessageContaining("PAUSED");
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
}
