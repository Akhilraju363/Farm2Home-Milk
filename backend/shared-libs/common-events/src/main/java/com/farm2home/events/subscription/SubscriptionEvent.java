package com.farm2home.events.subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Published to {@code subscription.events} by subscription-service on subscription lifecycle
 * changes. Field shape matches order-service's own local {@code SubscriptionEvent}
 * (order-service maintains its subscription_snapshots table from this same topic, parsed
 * independently rather than via this shared class - see
 * {@code order-service/.../kafka/SubscriptionEventConsumer}) so the two stay wire-compatible;
 * this class is what subscription-service's producer and notification-service's generic
 * {@code KafkaEventDto} pipeline actually consume.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionEvent {

    /** SUBSCRIPTION_CREATED | SUBSCRIPTION_PAUSED | SUBSCRIPTION_RESUMED | SUBSCRIPTION_CANCELLED */
    private String eventType;

    private UUID subscriptionId;
    private UUID customerId;
    private String milkType;
    private BigDecimal quantity;
    private String scheduleType;
    private List<String> deliveryDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime occurredAt;
}
