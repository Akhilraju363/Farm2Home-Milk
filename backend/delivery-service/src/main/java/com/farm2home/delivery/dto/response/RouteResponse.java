package com.farm2home.delivery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RouteResponse {

    @Schema(description = "Route id.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;

    @Schema(description = "Route name.", example = "North Zone Route 1")
    private String routeName;

    @Schema(description = "Unique short route code, always upper-case.", example = "NZ-01")
    private String routeCode;

    @Schema(description = "Locality/area served by this route.", example = "Whitefield")
    private String area;

    @Schema(description = "City served by this route.", example = "Bengaluru")
    private String city;

    @Schema(description = "Postal code.", example = "560066")
    private String pincode;

    @Schema(description = "Whether the route is currently active. Defaults to true on creation; "
            + "independent of soft-delete.", example = "true")
    private boolean active;

    @Schema(description = "When the route was created.", example = "2026-01-15T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Center of this route's coverage circle, used for automatic route "
            + "selection. Null if this route was never configured for it.", example = "13.67199825")
    private BigDecimal centerLatitude;

    @Schema(description = "See centerLatitude.", example = "78.96885066")
    private BigDecimal centerLongitude;

    @Schema(description = "Coverage radius in kilometers around (centerLatitude, centerLongitude).", example = "7.0")
    private BigDecimal radiusKm;
}
