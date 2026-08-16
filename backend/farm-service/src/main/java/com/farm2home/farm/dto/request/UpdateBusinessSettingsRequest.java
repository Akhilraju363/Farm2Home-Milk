package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateBusinessSettingsRequest {

    @NotNull(message = "Farm latitude is required")
    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    @Schema(example = "13.62708825")
    private BigDecimal farmLatitude;

    @NotNull(message = "Farm longitude is required")
    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    @Schema(example = "78.96885066")
    private BigDecimal farmLongitude;

    @NotNull(message = "Delivery radius is required")
    @DecimalMin(value = "0.01", message = "Delivery radius must be greater than 0")
    @DecimalMax(value = "1000", message = "Delivery radius must be at most 1000 km")
    @Schema(example = "10.00")
    private BigDecimal deliveryRadiusKm;
}
