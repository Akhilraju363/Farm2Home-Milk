package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateCowRequest {

    @Size(max = 100, message = "Cow name must be at most 100 characters")
    @Schema(description = "New cow name. Omit/null to leave unchanged.", example = "Bella")
    private String cowName;

    @Size(max = 100, message = "Breed must be at most 100 characters")
    @Schema(description = "New breed. Omit/null to leave unchanged.", example = "Jersey")
    private String breed;

    @Schema(description = "New date of birth. Omit/null to leave unchanged.", example = "2023-03-15")
    private LocalDate dateOfBirth;

    @Schema(description = "New purchase date. Omit/null to leave unchanged.", example = "2023-06-01")
    private LocalDate purchaseDate;

    @Schema(description = "New owning farm. Omit/null to leave unchanged.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID farmId;
}
