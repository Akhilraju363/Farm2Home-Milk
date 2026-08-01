package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateCowRequest {

    @NotBlank(message = "Tag number is required")
    @Size(max = 20, message = "Tag number must be at most 20 characters")
    @Schema(description = "Unique tag/ear-tag number identifying the cow. Must be unique among "
            + "non-deleted cows; normalized to upper-case on save.", example = "TAG001")
    private String tagNumber;

    @Schema(description = "Farm this cow belongs to. Optional - a cow can be registered before "
            + "being assigned to a farm.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID farmId;

    @Size(max = 100, message = "Cow name must be at most 100 characters")
    @Schema(description = "Optional friendly name for the cow.", example = "Bella")
    private String cowName;

    @NotBlank(message = "Breed is required")
    @Size(max = 100, message = "Breed must be at most 100 characters")
    @Schema(description = "Cow breed.", example = "Holstein")
    private String breed;

    @PastOrPresent(message = "Date of birth cannot be in the future")
    @Schema(description = "Date of birth. Must not be in the future.", example = "2023-03-15")
    private LocalDate dateOfBirth;

    @PastOrPresent(message = "Purchase date cannot be in the future")
    @Schema(description = "Date the cow was purchased/acquired. Must not be in the future.", example = "2023-06-01")
    private LocalDate purchaseDate;
}
