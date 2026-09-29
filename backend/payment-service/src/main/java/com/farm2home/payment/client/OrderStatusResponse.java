package com.farm2home.payment.client;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Minimal local projection of order-service's OrderResponse - only the fields payment-service
 *  actually needs to validate a payment against the order's current state. Jackson ignores the
 *  rest of the real response body (Spring Boot's default ObjectMapper does not fail on unknown
 *  properties), so this stays a thin, independent contract rather than a shared DTO coupling
 *  the two services' response shapes together.
 *
 *  customerId and totalAmount are the server-side source of truth for who pays and how much -
 *  initiate() never trusts the caller-supplied values for either. */
@Data
public class OrderStatusResponse {
    private UUID id;
    private UUID customerId;
    private String status;
    private BigDecimal totalAmount;
}
