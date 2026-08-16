package com.farm2home.inventory.client;

import lombok.Data;

import java.util.UUID;

/** Minimal local projection of order-service's real OrderResponse - only the fields review
 *  eligibility needs (ownership + delivery status). Jackson ignores the rest of the real
 *  response body. Mirrors invoice-service's OrderDetailResponse. */
@Data
public class OrderDetailResponse {
    private UUID id;
    private String orderNumber;
    private UUID customerId;
    private String status;
}
