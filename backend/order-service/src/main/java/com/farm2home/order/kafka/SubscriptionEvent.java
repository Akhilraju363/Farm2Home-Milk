package com.farm2home.order.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consumed from the {@code subscription.events} Kafka topic.
 * Published by subscription-service on subscription lifecycle changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEvent {

    /** CREATED | UPDATED | PAUSED | RESUMED | CANCELLED | EXPIRED */
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
}
