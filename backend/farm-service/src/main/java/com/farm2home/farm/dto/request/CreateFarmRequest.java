package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFarmRequest {

    @NotBlank(message = "Farm name is required")
    @Size(max = 150, message = "Farm name must be at most 150 characters")
    @Schema(description = "Name of the farm.", example = "Green Valley Dairy Farm")
    private String farmName;

    @NotBlank(message = "Owner name is required")
    @Size(max = 150, message = "Owner name must be at most 150 characters")
    @Schema(description = "Name of the farm's owner.", example = "Ramesh Kumar")
    private String ownerName;

    @Size(max = 255, message = "Location must be at most 255 characters")
    @Schema(description = "Optional physical location/address of the farm.", example = "Nashik, Maharashtra")
    private String location;

    @Schema(description = "Optional free-text description of the farm.", example = "15-acre dairy farm specializing in A2 milk.")
    private String description;
}
