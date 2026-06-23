package com.farm2home.delivery.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryEvent {
    private String eventType;   // DELIVERY_ASSIGNED | DELIVERY_COMPLETED | DELIVERY_FAILED
    private UUID assignmentId;
    private UUID orderId;
    private UUID deliveryPartnerId;
    private String deliveryPartnerName;
    private String status;
    private String failureReason;
    private LocalDateTime occurredAt;
}
