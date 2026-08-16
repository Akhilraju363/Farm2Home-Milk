package com.farm2home.delivery.client;

import lombok.Data;

import java.util.UUID;

/** Minimal local projection of order-service's real OrderResponse - only the fields
 *  manualAssign() needs: id/orderNumber/customerId to backfill onto a manually-created
 *  DeliveryAssignment (see OrderEventConsumer, which already populates these for
 *  auto-assignments from the Kafka event payload; manualAssign never had an equivalent source
 *  until now), status to verify the order is actually eligible for a new assignment (PENDING -
 *  matches order-service's own OrderStatus enum exactly), and deliveryRouteId - the route
 *  order-service already selected automatically at order-creation time from the customer's
 *  delivery address (see OrderServiceImpl.verifyDeliveryEligibility), which manualAssign uses by
 *  default instead of trusting a client-submitted routeId. */
@Data
public class OrderDetailResponse {
    private UUID id;
    private String orderNumber;
    private UUID customerId;
    private String status;
    private UUID deliveryRouteId;
}
