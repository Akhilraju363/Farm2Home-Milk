package com.farm2home.delivery.client;

import lombok.Data;

import java.util.UUID;

/** Minimal local projection of order-service's real OrderResponse - only the fields
 *  manualAssign() needs to backfill onto a manually-created DeliveryAssignment (see
 *  OrderEventConsumer, which already populates these for auto-assignments from the Kafka event
 *  payload; manualAssign never had an equivalent source until now). */
@Data
public class OrderDetailResponse {
    private UUID id;
    private String orderNumber;
    private UUID customerId;
}
