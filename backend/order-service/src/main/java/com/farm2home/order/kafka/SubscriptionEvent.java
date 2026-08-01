package com.farm2home.order.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Published by subscription-service on subscription lifecycle changes (see
 * {@code com.farm2home.events.subscription.SubscriptionEvent} in common-events, the class the
 * producer actually builds - this is a separate, independently-maintained copy parsed manually
 * via {@link com.fasterxml.jackson.databind.ObjectMapper#readValue}, not the shared type itself).
 * {@code ignoreUnknown = true} so this class can safely fall behind the producer's own field set
 * (e.g. an added {@code occurredAt}) without breaking deserialization here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
