package com.farm2home.order.service;

import com.farm2home.order.config.MilkPriceProperties;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.SubscriptionSnapshot;
import com.farm2home.order.domain.enums.*;
import com.farm2home.order.domain.repository.OrderRepository;
import com.farm2home.order.domain.repository.SubscriptionSnapshotRepository;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.kafka.OrderEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyOrderGenerationServiceTest {

    @Mock private SubscriptionSnapshotRepository snapshotRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private MilkPriceProperties priceProperties;
    @Mock private OrderEventProducer eventProducer;

    @InjectMocks private DailyOrderGenerationService service;

    private final UUID subId      = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private SubscriptionSnapshot buildSnapshot(ScheduleType scheduleType, LocalDate startDate,
                                                List<DeliveryDay> deliveryDays) {
        return SubscriptionSnapshot.builder()
                .subscriptionId(subId)
                .customerId(customerId)
                .milkType(MilkType.FULL_CREAM)
                .quantity(new BigDecimal("1.5"))
                .scheduleType(scheduleType)
                .deliveryDays(deliveryDays)
                .startDate(startDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    @BeforeEach
    void setupPrices() {
        when(priceProperties.getPriceFor("FULL_CREAM")).thenReturn(new BigDecimal("80.00"));
    }

    // ── isDueOn ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isDueOn() — delivery schedule logic")
    class IsDueOn {

        @Test
        @DisplayName("DAILY subscription is due every day after start")
        void daily_alwaysDue() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.DAILY, LocalDate.now().minusDays(5), null);
            assertThat(service.isDueOn(s, LocalDate.now())).isTrue();
            assertThat(service.isDueOn(s, LocalDate.now().plusDays(10))).isTrue();
        }

        @Test
        @DisplayName("DAILY subscription is NOT due before start date")
        void daily_notDueBeforeStart() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.DAILY, LocalDate.now().plusDays(3), null);
            assertThat(service.isDueOn(s, LocalDate.now())).isFalse();
        }

        @Test
        @DisplayName("DAILY subscription is NOT due after end date")
        void daily_notDueAfterEnd() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.DAILY, LocalDate.now().minusDays(10), null);
            s.setEndDate(LocalDate.now().minusDays(1));
            assertThat(service.isDueOn(s, LocalDate.now())).isFalse();
        }

        @Test
        @DisplayName("ALTERNATE_DAY on day 0 (start date) is due")
        void alternateDay_dayZero() {
            LocalDate start = LocalDate.now();
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.ALTERNATE_DAY, start, null);
            assertThat(service.isDueOn(s, start)).isTrue();
        }

        @Test
        @DisplayName("ALTERNATE_DAY on day 1 (one day after start) is NOT due")
        void alternateDay_dayOne() {
            LocalDate start = LocalDate.now().minusDays(1);
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.ALTERNATE_DAY, start, null);
            assertThat(service.isDueOn(s, LocalDate.now())).isFalse();
        }

        @Test
        @DisplayName("ALTERNATE_DAY on day 2 (two days after start) is due")
        void alternateDay_dayTwo() {
            LocalDate start = LocalDate.now().minusDays(2);
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.ALTERNATE_DAY, start, null);
            assertThat(service.isDueOn(s, LocalDate.now())).isTrue();
        }

        @Test
        @DisplayName("WEEKLY on correct delivery day is due")
        void weekly_correctDay() {
            // Find the next Monday
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.WEEKLY, monday.minusWeeks(1),
                    List.of(DeliveryDay.MON, DeliveryDay.THU));
            assertThat(service.isDueOn(s, monday)).isTrue();
        }

        @Test
        @DisplayName("WEEKLY on wrong delivery day is NOT due")
        void weekly_wrongDay() {
            LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.WEEKLY, tuesday.minusWeeks(1),
                    List.of(DeliveryDay.MON, DeliveryDay.THU));
            assertThat(service.isDueOn(s, tuesday)).isFalse();
        }

        @Test
        @DisplayName("WEEKLY with empty delivery days is never due")
        void weekly_noDays() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.WEEKLY,
                    LocalDate.now().minusDays(7), Collections.emptyList());
            assertThat(service.isDueOn(s, LocalDate.now())).isFalse();
        }
    }

    // ── generateOrdersForDate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateOrdersForDate()")
    class GenerateOrders {

        @Test
        @DisplayName("generates one order for one active daily subscription")
        void generatesOrderForDaily() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.DAILY, LocalDate.now().minusDays(1), null);
            LocalDate today = LocalDate.now();

            when(snapshotRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(s));
            when(orderRepository.existsBySubscriptionIdAndOrderDateAndDeletedFalse(subId, today))
                    .thenReturn(false);
            when(orderRepository.nextOrderNumber()).thenReturn(100001L);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GenerationResultResponse result = service.generateOrdersForDate(today);

            assertThat(result.getOrdersCreated()).isEqualTo(1);
            assertThat(result.getSkipped()).isEqualTo(0);
            verify(orderRepository).save(any(Order.class));
            verify(eventProducer).publishOrderCreated(any());
        }

        @Test
        @DisplayName("skips order if one already exists for subscription+date (idempotency)")
        void idempotency_skipsDuplicate() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.DAILY, LocalDate.now().minusDays(1), null);
            LocalDate today = LocalDate.now();

            when(snapshotRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(s));
            when(orderRepository.existsBySubscriptionIdAndOrderDateAndDeletedFalse(subId, today))
                    .thenReturn(true);

            GenerationResultResponse result = service.generateOrdersForDate(today);

            assertThat(result.getOrdersCreated()).isEqualTo(0);
            assertThat(result.getSkipped()).isEqualTo(1);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips subscription that is not due today (wrong WEEKLY day)")
        void skipsDueToSchedule() {
            // Find a Tuesday to ensure it's the wrong day for MON/THU subscription
            LocalDate tuesday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.WEEKLY, tuesday.minusWeeks(1),
                    List.of(DeliveryDay.MON, DeliveryDay.THU));

            when(snapshotRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(s));

            GenerationResultResponse result = service.generateOrdersForDate(tuesday);

            assertThat(result.getOrdersCreated()).isEqualTo(0);
            assertThat(result.getSkipped()).isEqualTo(1);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("no active snapshots → zero orders generated")
        void noActiveSnapshots() {
            when(snapshotRepository.findAllByStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            GenerationResultResponse result = service.generateOrdersForDate(LocalDate.now());

            assertThat(result.getOrdersCreated()).isEqualTo(0);
            assertThat(result.getSubscriptionsProcessed()).isEqualTo(0);
        }

        @Test
        @DisplayName("calculates correct total amount (quantity × unit price)")
        void correctTotalAmount() {
            SubscriptionSnapshot s = buildSnapshot(ScheduleType.DAILY, LocalDate.now().minusDays(1), null);
            LocalDate today = LocalDate.now();

            when(snapshotRepository.findAllByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(s));
            when(orderRepository.existsBySubscriptionIdAndOrderDateAndDeletedFalse(subId, today))
                    .thenReturn(false);
            when(orderRepository.nextOrderNumber()).thenReturn(100001L);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.generateOrdersForDate(today);

            verify(orderRepository).save(argThat(order ->
                    // 1.5 litres × 80.00 = 120.00
                    order.getTotalAmount().compareTo(new BigDecimal("120.00")) == 0));
        }
    }
}
