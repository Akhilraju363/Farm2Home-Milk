package com.farm2home.farm.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FarmResponse {

    @Schema(description = "Farm id.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;

    @Schema(description = "Farm name.", example = "Green Valley Dairy Farm")
    private String farmName;

    @Schema(description = "Owner's name.", example = "Ramesh Kumar")
    private String ownerName;

    @Schema(description = "Physical location/address, if set.", example = "Nashik, Maharashtra")
    private String location;

    @Schema(description = "Free-text description, if set.", example = "15-acre dairy farm specializing in A2 milk.")
    private String description;

    @Schema(description = "Relative URL of the farm's image, if one has been uploaded via POST /{id}/image.",
            example = "/uploads/farms/8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b.jpg")
    private String imageUrl;

    @Schema(description = "Record creation timestamp.", example = "2026-06-01T09:00:00")
    private LocalDateTime createdAt;
}
