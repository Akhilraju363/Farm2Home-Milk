package com.farm2home.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitLocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(example = "17.4123")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(example = "78.4482")
    private Double longitude;

    @Schema(description = "Device-reported GPS accuracy radius in meters, if available.", example = "12.5")
    private Double accuracy;

    @Schema(description = "Device-reported speed in meters/second, if available.", example = "8.3")
    private Double speed;

    @Schema(description = "Device-reported heading in degrees, 0-360 (0 = true north), if available.", example = "245.0")
    @DecimalMin(value = "0.0", message = "Heading must be between 0 and 360")
    @DecimalMax(value = "360.0", message = "Heading must be between 0 and 360")
    private Double heading;
}
