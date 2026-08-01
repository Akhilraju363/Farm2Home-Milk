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
public class VaccinationResponse {

    @Schema(description = "Vaccination record id.", example = "1a2b3c4d-5e6f-4a5b-8c9d-0e1f2a3b4c5d")
    private UUID id;

    @Schema(description = "Id of the cow this vaccination belongs to.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID cowId;

    @Schema(description = "Tag number of the cow this vaccination belongs to, denormalized for display.", example = "TAG001")
    private String tagNumber;

    @Schema(description = "Name of the vaccine administered.", example = "Foot-and-Mouth Disease (FMD) Vaccine")
    private String vaccineName;

    @Schema(description = "Date the vaccine was administered.", example = "2026-07-15")
    private LocalDate administeredAt;

    @Schema(description = "Date the next dose is due, if applicable. Drives GET /vaccinations/upcoming.",
            example = "2027-01-15")
    private LocalDate nextDueDate;

    @Schema(description = "Person/vet who administered the vaccine, if recorded.", example = "Dr. Suresh Patil")
    private String administeredBy;

    @Schema(description = "Free-text notes, if recorded.", example = "No adverse reaction observed.")
    private String notes;

    @Schema(description = "Record creation timestamp.", example = "2026-07-15T10:30:00")
    private LocalDateTime createdAt;
}
