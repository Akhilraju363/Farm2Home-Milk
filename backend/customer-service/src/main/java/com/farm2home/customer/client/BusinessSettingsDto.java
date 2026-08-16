package com.farm2home.customer.client;

import lombok.Data;

import java.math.BigDecimal;

/** Local projection of farm-service's BusinessSettingsResponse - only the fields this service
 *  needs (Jackson ignores the rest, e.g. updatedAt). */
@Data
public class BusinessSettingsDto {
    private BigDecimal farmLatitude;
    private BigDecimal farmLongitude;
    private BigDecimal deliveryRadiusKm;
}
