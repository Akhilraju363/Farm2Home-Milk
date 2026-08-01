package com.farm2home.order.service;

import com.farm2home.order.config.MilkPriceProperties;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.domain.entity.SubscriptionSnapshot;
import com.farm2home.order.domain.enums.DeliveryDay;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.enums.OrderType;
import com.farm2home.order.domain.enums.SubscriptionStatus;
import com.farm2home.order.domain.repository.OrderRepository;
import com.farm2home.order.domain.repository.SubscriptionSnapshotRepository;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.kafka.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Generates subscription-based orders daily.
 * Reads from the local subscription_snapshots table (populated by Kafka consumer).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyOrderGenerationService {

    private final SubscriptionSnapshotRepository snapshotRepository;
    private final OrderRepository orderRepository;
    private final MilkPriceProperties priceProperties;
    private final OrderEventProducer eventProducer;

    /** Runs nightly at 23:00. Generates tomorrow's orders, so delivery-service has a full
     *  night's lead time to assign partners/plan routes before the morning. */
    @Scheduled(cron = "0 0 23 * * *")
    public void generateForTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("Starting scheduled order generation for {}", tomorrow);
        GenerationResultResponse result = generateOrdersForDate(tomorrow);
        log.info("Order generation complete: {} orders created, {} skipped",
                result.getOrdersCreated(), result.getSkipped());
    }

    @Transactional
    public GenerationResultResponse generateOrdersForDate(LocalDate date) {
        List<SubscriptionSnapshot> activeSnapshots =
                snapshotRepository.findAllByStatus(SubscriptionStatus.ACTIVE);

        int created = 0;
        int skipped = 0;

        for (SubscriptionSnapshot snapshot : activeSnapshots) {
            if (!isDueOn(snapshot, date)) {
                skipped++;
                continue;
            }

            // Idempotency: skip if order for this subscription+date already exists
            if (orderRepository.existsBySubscriptionIdAndOrderDateAndDeletedFalse(
                    snapshot.getSubscriptionId(), date)) {
                log.debug("Order already exists for subscription {} on {} — skipping",
                        snapshot.getSubscriptionId(), date);
                skipped++;
                continue;
            }

            Order order = buildSubscriptionOrder(snapshot, date);
            Order saved = orderRepository.save(order);
            eventProducer.publishOrderCreated(saved);
            created++;
        }

        return GenerationResultResponse.builder()
                .date(date)
                .subscriptionsProcessed(activeSnapshots.size())
                .ordersCreated(created)
                .skipped(skipped)
                .message(String.format("Generated %d orders for %s", created, date))
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    boolean isDueOn(SubscriptionSnapshot snapshot, LocalDate date) {
        if (date.isBefore(snapshot.getStartDate())) return false;
        if (snapshot.getEndDate() != null && date.isAfter(snapshot.getEndDate())) return false;

        return switch (snapshot.getScheduleType()) {
            case DAILY -> true;
            case ALTERNATE_DAY ->
                    ChronoUnit.DAYS.between(snapshot.getStartDate(), date) % 2 == 0;
            case WEEKLY -> {
                DeliveryDay today = toDeliveryDay(date.getDayOfWeek());
                yield snapshot.getDeliveryDays() != null
                        && snapshot.getDeliveryDays().contains(today);
            }
        };
    }

    private DeliveryDay toDeliveryDay(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> DeliveryDay.MON;
            case TUESDAY   -> DeliveryDay.TUE;
            case WEDNESDAY -> DeliveryDay.WED;
            case THURSDAY  -> DeliveryDay.THU;
            case FRIDAY    -> DeliveryDay.FRI;
            case SATURDAY  -> DeliveryDay.SAT;
            case SUNDAY    -> DeliveryDay.SUN;
        };
    }

    private Order buildSubscriptionOrder(SubscriptionSnapshot snapshot, LocalDate date) {
        BigDecimal unitPrice  = priceProperties.getPriceFor(snapshot.getMilkType().name());
        BigDecimal totalPrice = snapshot.getQuantity().multiply(unitPrice);

        String orderNumber = String.format("ORD-%d-%06d", date.getYear(),
                orderRepository.nextOrderNumber());

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customerId(snapshot.getCustomerId())
                .subscriptionId(snapshot.getSubscriptionId())
                .orderDate(date)
                .orderType(OrderType.SUBSCRIPTION)
                .totalAmount(totalPrice)
                .status(OrderStatus.PENDING)
                .build();

        OrderItem item = OrderItem.builder()
                .milkType(snapshot.getMilkType())
                .quantity(snapshot.getQuantity())
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .build();

        order.addItem(item);
        return order;
    }
}
