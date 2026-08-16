package com.farm2home.order.client;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Local projection of customer-service's DeliveryAvailabilityResponse. deliveryAvailable is
 *  Boolean (not boolean) - null means "could not be determined", which OrderServiceImpl treats
 *  as non-blocking (see createManualOrder), distinct from a confirmed false. routeId is the
 *  automatically-selected route (customer-service's DeliveryRouteSelectionServiceImpl, the sole
 *  authoritative implementation) - order-service persists it onto the Order verbatim, never
 *  re-deriving it. */
@Data
public class DeliveryAvailabilityResponse {
    private Boolean deliveryAvailable;
    private BigDecimal distanceKm;
    private BigDecimal deliveryRadiusKm;
    private String message;
    private UUID routeId;
}
