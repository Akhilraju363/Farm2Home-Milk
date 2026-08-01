package com.farm2home.farm.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateVaccinationRequest {

    @Size(max = 100, message = "Vaccine name must be at most 100 characters")
    @Schema(description = "New vaccine name. Omit/null to leave unchanged.", example = "Foot-and-Mouth Disease (FMD) Vaccine")
    private String vaccineName;

    @PastOrPresent(message = "Administered date cannot be in the future")
    @Schema(description = "New administered date. Must not be in the future. Omit/null to leave unchanged.",
            example = "2026-07-15")
    private LocalDate administeredAt;

    @Schema(description = "New next-due date. Omit/null to leave unchanged.", example = "2027-01-15")
    private LocalDate nextDueDate;

    @Size(max = 100, message = "Administered by must be at most 100 characters")
    @Schema(description = "New administered-by name. Omit/null to leave unchanged.", example = "Dr. Suresh Patil")
    private String administeredBy;

    @Size(max = 255, message = "Notes must be at most 255 characters")
    @Schema(description = "New notes. Omit/null to leave unchanged.", example = "No adverse reaction observed.")
    private String notes;
}
