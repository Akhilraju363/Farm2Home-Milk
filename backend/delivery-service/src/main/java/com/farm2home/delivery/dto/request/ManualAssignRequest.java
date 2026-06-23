package com.farm2home.delivery.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ManualAssignRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    @NotNull(message = "Delivery partner ID is required")
    private UUID deliveryPartnerId;

    @NotNull(message = "Route ID is required")
    private UUID routeId;
}
