package com.farm2home.events.delivery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published to {@code delivery.events}. eventType: DELIVERY_ASSIGNED | DELIVERY_COMPLETED | DELIVERY_FAILED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryEvent {

    private String eventType;
    private UUID assignmentId;
    private UUID orderId;
    private String orderNumber;
    private UUID customerId;
    private UUID deliveryPartnerId;
    private String deliveryPartnerName;
    private String status;
    private String failureReason;
    private LocalDateTime occurredAt;
}
