package com.farm2home.order.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Consumed from the {@code delivery.events} Kafka topic.
 * Published by delivery-service on delivery assignment status changes (see
 * {@code com.farm2home.events.delivery.DeliveryEvent} in common-events, the class the producer
 * actually builds - this is a separate, independently-maintained copy parsed manually via
 * {@link com.fasterxml.jackson.databind.ObjectMapper#readValue}, not the shared type itself, same
 * convention as {@link SubscriptionEvent}). {@code ignoreUnknown = true} so this class can safely
 * fall behind the producer's own field set without breaking deserialization here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryEvent {

    /** DELIVERY_ASSIGNED | DELIVERY_OUT_FOR_DELIVERY | DELIVERY_COMPLETED | DELIVERY_FAILED */
    private String eventType;

    private UUID orderId;

    /** Mirrors delivery-service's AssignmentStatus name: ASSIGNED | OUT_FOR_DELIVERY | DELIVERED | FAILED. */
    private String status;
}
