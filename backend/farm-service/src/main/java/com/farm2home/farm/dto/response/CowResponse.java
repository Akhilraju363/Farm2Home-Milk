package com.farm2home.farm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CowResponse {

    @Schema(description = "Cow id.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID id;

    @Schema(description = "Tag/ear-tag number, unique among non-deleted cows.", example = "TAG001")
    private String tagNumber;

    @Schema(description = "Friendly name, if set.", example = "Bella")
    private String cowName;

    @Schema(description = "Breed.", example = "Holstein")
    private String breed;

    @Schema(description = "Date of birth, if known.", example = "2023-03-15")
    private LocalDate dateOfBirth;

    @Schema(description = "Date purchased/acquired, if known.", example = "2023-06-01")
    private LocalDate purchaseDate;

    @Schema(description = "Owning farm id, if assigned.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID farmId;

    @Schema(description = "Current status. ACTIVE/SICK can transition to any status; SOLD/DECEASED are terminal.",
            example = "ACTIVE")
    private String status;

    @Schema(description = "Record creation timestamp.", example = "2026-06-01T09:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Record last-updated timestamp.", example = "2026-07-30T14:22:10")
    private LocalDateTime updatedAt;
}
