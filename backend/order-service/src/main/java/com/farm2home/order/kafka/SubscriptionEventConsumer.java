package com.farm2home.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm2home.common.core.constants.KafkaTopics;
import com.farm2home.order.domain.entity.SubscriptionSnapshot;
import com.farm2home.order.domain.enums.DeliveryDay;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.ScheduleType;
import com.farm2home.order.domain.enums.SubscriptionStatus;
import com.farm2home.order.domain.repository.SubscriptionSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Maintains the local subscription_snapshots table from subscription-service events.
 * This decouples daily order generation from runtime subscription-service availability.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventConsumer {

    private final SubscriptionSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.SUBSCRIPTION_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            SubscriptionEvent event = objectMapper.readValue(message, SubscriptionEvent.class);
            log.info("Received subscription event: {} for subscriptionId {}",
                    event.getEventType(), event.getSubscriptionId());
            handleEvent(event);
        } catch (Exception e) {
            log.error("Failed to process subscription event: {}", e.getMessage(), e);
        }
    }

    private void handleEvent(SubscriptionEvent event) {
        SubscriptionStatus status = SubscriptionStatus.valueOf(event.getStatus());

        if (status == SubscriptionStatus.CANCELLED || status == SubscriptionStatus.EXPIRED) {
            snapshotRepository.findById(event.getSubscriptionId()).ifPresent(snapshot -> {
                snapshot.setStatus(status);
                snapshot.setUpdatedAt(LocalDateTime.now());
                snapshotRepository.save(snapshot);
            });
            return;
        }

        List<DeliveryDay> deliveryDays = event.getDeliveryDays() == null
                ? Collections.emptyList()
                : event.getDeliveryDays().stream().map(DeliveryDay::valueOf).toList();

        SubscriptionSnapshot snapshot = SubscriptionSnapshot.builder()
                .subscriptionId(event.getSubscriptionId())
                .customerId(event.getCustomerId())
                .milkType(MilkType.valueOf(event.getMilkType()))
                .quantity(event.getQuantity())
                .scheduleType(ScheduleType.valueOf(event.getScheduleType()))
                .deliveryDays(deliveryDays)
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .status(status)
                .updatedAt(LocalDateTime.now())
                .build();

        snapshotRepository.save(snapshot);
        log.debug("Upserted subscription snapshot for {}", event.getSubscriptionId());
    }
}
