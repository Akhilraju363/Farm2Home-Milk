package com.farm2home.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {

    @Schema(example = "a1b2c3d4-e5f6-4789-9abc-1234567890ab")
    private UUID id;

    @Schema(example = "ORD-2026-100042")
    private String orderNumber;

    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;

    @Schema(description = "Null for a manual one-time order; set when generated from a subscription",
            example = "9c3f2b1a-4d5e-4f6a-8b7c-1a2b3c4d5e6f")
    private UUID subscriptionId;

    @Schema(description = "Automatically-selected delivery route for this order's delivery "
            + "address (see customer-service's DeliveryRouteSelectionServiceImpl). Never settable "
            + "by the client. Null if no active route's coverage circle contained the address, or "
            + "this order predates the feature - the frontend shows \"Route not assigned\" rather "
            + "than treating this as an error.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID deliveryRouteId;

    @Schema(example = "2026-06-25")
    private LocalDate orderDate;

    @Schema(description = "MANUAL or SUBSCRIPTION", example = "MANUAL")
    private String orderType;

    @Schema(description = "PENDING, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, or CANCELLED - see "
            + "PATCH /{id}/status for the valid transition rules", example = "PENDING")
    private String status;

    @Schema(description = "Sum of every item's totalPrice, in rupees", example = "120.00")
    private BigDecimal totalAmount;

    @Schema(example = "Please leave at the door")
    private String notes;

    private List<OrderItemResponse> items;

    @Schema(example = "2026-06-24T18:30:00")
    private LocalDateTime createdAt;

    @Schema(example = "2026-06-24T18:30:00")
    private LocalDateTime updatedAt;
}
