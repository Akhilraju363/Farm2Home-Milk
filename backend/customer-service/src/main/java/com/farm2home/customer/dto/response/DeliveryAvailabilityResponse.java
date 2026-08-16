package com.farm2home.customer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DeliveryAvailabilityResponse {
    @Schema(description = "True/false when it could be determined; null when the address has no "
            + "captured coordinates yet, or the customer has no address at all - never assume "
            + "availability in that case.", example = "true")
    private Boolean deliveryAvailable;
    @Schema(description = "Null when deliveryAvailable is null (distance could not be computed).", example = "7.42")
    private BigDecimal distanceKm;
    @Schema(example = "10")
    private BigDecimal deliveryRadiusKm;
    @Schema(description = "Human-readable explanation - populated when unavailable or unknown, null when available.",
            example = "Delivery is currently unavailable at this location.")
    private String message;

    @Schema(description = "The automatically-selected delivery route for this address (see "
            + "DeliveryRouteSelectionServiceImpl - the single authoritative implementation). Null "
            + "when deliveryAvailable isn't true, or no active route's coverage circle actually "
            + "contains this address (a route-coverage gap, logged for administrators - never "
            + "blocks the underlying delivery-availability result).",
            example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID routeId;
    @Schema(description = "Display name of routeId - internal route codes are deliberately not "
            + "exposed here (see the Shop/Checkout UI, which shows only this).", example = "Farm2Home North")
    private String routeName;
}
