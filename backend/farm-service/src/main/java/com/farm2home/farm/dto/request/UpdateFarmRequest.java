package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFarmRequest {

    @Size(max = 150, message = "Farm name must be at most 150 characters")
    @Schema(description = "New farm name. Omit/null to leave unchanged.", example = "Green Valley Dairy Farm")
    private String farmName;

    @Size(max = 150, message = "Owner name must be at most 150 characters")
    @Schema(description = "New owner name. Omit/null to leave unchanged.", example = "Ramesh Kumar")
    private String ownerName;

    @Size(max = 255, message = "Location must be at most 255 characters")
    @Schema(description = "New location/address. Omit/null to leave unchanged.", example = "Nashik, Maharashtra")
    private String location;

    @Schema(description = "New description. Omit/null to leave unchanged.", example = "15-acre dairy farm specializing in A2 milk.")
    private String description;
}
