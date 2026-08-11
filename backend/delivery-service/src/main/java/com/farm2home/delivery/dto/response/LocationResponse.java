package com.farm2home.delivery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LocationResponse {

    @Schema(example = "c1a2b3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
    private UUID deliveryAssignmentId;

    @Schema(example = "17.4123")
    private Double latitude;

    @Schema(example = "78.4482")
    private Double longitude;

    private Double accuracy;

    private Double speed;

    private Double heading;

    @Schema(description = "Server timestamp when this reading was received - not a client-supplied value.",
            example = "2026-08-10T14:32:05")
    private LocalDateTime recordedAt;

    @Schema(description = "LIVE (<1 min old), RECENT (1-5 min), or STALE (>5 min) - derived from recordedAt "
            + "at response time, not stored.", example = "LIVE")
    private String freshness;
}
