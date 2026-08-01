package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateVaccinationRequest {

    @NotBlank(message = "Vaccine name is required")
    @Size(max = 100, message = "Vaccine name must be at most 100 characters")
    @Schema(description = "Name of the vaccine administered.", example = "Foot-and-Mouth Disease (FMD) Vaccine")
    private String vaccineName;

    @NotNull(message = "Administered date is required")
    @PastOrPresent(message = "Administered date cannot be in the future")
    @Schema(description = "Date the vaccine was administered. Must not be in the future.", example = "2026-07-15")
    private LocalDate administeredAt;

    @Schema(description = "Optional date the next dose is due. When set, this record is surfaced by "
            + "GET /vaccinations/upcoming once the due date is within the requested look-ahead window.",
            example = "2027-01-15")
    private LocalDate nextDueDate;

    @Size(max = 100, message = "Administered by must be at most 100 characters")
    @Schema(description = "Optional name of the person/vet who administered the vaccine.", example = "Dr. Suresh Patil")
    private String administeredBy;

    @Size(max = 255, message = "Notes must be at most 255 characters")
    @Schema(description = "Optional free-text notes.", example = "No adverse reaction observed.")
    private String notes;
}
