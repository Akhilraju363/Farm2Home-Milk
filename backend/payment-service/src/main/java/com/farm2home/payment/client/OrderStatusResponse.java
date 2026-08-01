package com.farm2home.payment.client;

import lombok.Data;

import java.util.UUID;

/** Minimal local projection of order-service's OrderResponse - only the fields payment-service
 *  actually needs to validate a payment against the order's current state. Jackson ignores the
 *  rest of the real response body (Spring Boot's default ObjectMapper does not fail on unknown
 *  properties), so this stays a thin, independent contract rather than a shared DTO coupling
 *  the two services' response shapes together. */
@Data
public class OrderStatusResponse {
    private UUID id;
    private String status;
}
