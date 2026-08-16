package com.farm2home.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartnerResponse {

    @Schema(description = "Delivery partner's own id.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID id;

    @Schema(description = "auth-service user id of the partner's account.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID userId;

    @Schema(description = "Assigned route id, if any.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID routeId;

    @Schema(description = "Assigned route's short code, denormalized at read time. Null if no "
            + "route is assigned.", example = "NZ-01")
    private String routeCode;

    @Schema(description = "Partner's display name.", example = "Ramesh Kumar")
    private String name;

    @Schema(description = "Partner's contact mobile number.", example = "9876543210")
    private String mobile;

    @Schema(description = "Free-text vehicle type/description.", example = "Two-wheeler")
    private String vehicleType;

    @Schema(description = "Whether the partner is currently active. Defaults to true on creation.", example = "true")
    private boolean active;

    @Schema(description = "When the partner record was created.", example = "2026-01-15T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Current count of non-terminal (ASSIGNED/OUT_FOR_DELIVERY) assignments - "
            + "the same live workload PartnerSelectionServiceImpl uses to pick the least-loaded "
            + "partner, exposed here for admin visibility (see DeliveryPartnerServiceImpl).", example = "2")
    private long activeDeliveries;
}
