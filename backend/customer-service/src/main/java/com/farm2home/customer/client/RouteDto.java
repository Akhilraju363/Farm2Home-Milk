package com.farm2home.customer.client;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Local projection of delivery-service's real RouteResponse - only the fields
 *  DeliveryRouteSelectionServiceImpl needs to run its own distance calculation (never delivery-
 *  service's own, keeping exactly one authoritative route-selection implementation). */
@Data
public class RouteDto {
    private UUID id;
    private String routeName;
    private String routeCode;
    private BigDecimal centerLatitude;
    private BigDecimal centerLongitude;
    private BigDecimal radiusKm;
}
