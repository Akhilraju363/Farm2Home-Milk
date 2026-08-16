package com.farm2home.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ManualAssignRequest {

    @NotNull(message = "Order ID is required")
    @Schema(description = "Order to assign for delivery. Must not already have an assignment "
            + "(DeliveryAssignmentServiceImpl rejects a second assignment for the same order).",
            example = "b3f1a2e4-5c6d-4e7f-8a9b-0c1d2e3f4a5b", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID orderId;

    @NotNull(message = "Delivery partner ID is required")
    @Schema(description = "Delivery partner to assign the order to. Must reference an existing, "
            + "non-deleted delivery partner.",
            example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID deliveryPartnerId;

    @Schema(description = "Explicit admin override for the route - optional. The order's own "
            + "automatically-selected route (Order.deliveryRouteId, computed at order-creation "
            + "time from the customer's delivery address - see order-service's "
            + "verifyDeliveryEligibility) is used when this is omitted. Provide this only to "
            + "deliberately change the route (e.g. the customer's address changed after the order "
            + "was placed). Either way, the resolved route must reference an existing, "
            + "non-deleted, active route.",
            example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID routeId;
}
