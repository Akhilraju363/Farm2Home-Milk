package com.farm2home.subscription.kafka;

import com.farm2home.common.core.constants.KafkaTopics;
import com.farm2home.events.subscription.SubscriptionEvent;
import com.farm2home.subscription.domain.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publishes subscription lifecycle changes to {@code subscription.events} - the topic
 * order-service (subscription snapshot sync) and notification-service (Subscription Alerts,
 * see PushService/SmsService wiring in NotificationServiceImpl) already consume from, but that
 * had no producer until now (see the removed comment on
 * {@code SubscriptionServiceImpl.checkUpcomingRenewals()} and {@code KafkaTopics.SUBSCRIPTION_EVENTS}'s
 * own javadoc, both of which explicitly noted the missing producer).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventProducer {

    private static final String TOPIC = KafkaTopics.SUBSCRIPTION_EVENTS;

    private final KafkaTemplate<String, SubscriptionEvent> subscriptionKafkaTemplate;

    public void publish(String eventType, Subscription subscription) {
        SubscriptionEvent event = SubscriptionEvent.builder()
                .eventType(eventType)
                .subscriptionId(subscription.getId())
                .customerId(subscription.getCustomerId())
                .milkType(subscription.getMilkType().name())
                .quantity(subscription.getQuantity())
                .scheduleType(subscription.getScheduleType().name())
                .deliveryDays(subscription.getDeliveryDays() == null ? List.of()
                        : subscription.getDeliveryDays().stream().map(Enum::name).toList())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus().name())
                .occurredAt(LocalDateTime.now())
                .build();

        subscriptionKafkaTemplate.send(TOPIC, subscription.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish SubscriptionEvent [{}] for subscription {}: {}",
                                eventType, subscription.getId(), ex.getMessage());
                    } else {
                        log.debug("Published SubscriptionEvent [{}] for subscription {}", eventType, subscription.getId());
                    }
                });
    }
}
