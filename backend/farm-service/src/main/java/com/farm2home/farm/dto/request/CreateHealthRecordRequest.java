package com.farm2home.farm.dto.request;

import com.farm2home.farm.domain.enums.HealthCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateHealthRecordRequest {

    @NotNull(message = "Record date is required")
    @PastOrPresent(message = "Record date cannot be in the future")
    @Schema(description = "Date the health observation/treatment took place. Must not be in the future.",
            example = "2026-07-28")
    private LocalDate recordDate;

    @NotNull(message = "Condition is required")
    @Schema(description = "Cow's health condition at the time of this record.", example = "SICK")
    private HealthCondition condition;

    @Size(max = 255, message = "Symptoms must be at most 255 characters")
    @Schema(description = "Optional free-text symptoms observed.", example = "Reduced appetite, mild fever")
    private String symptoms;

    @Size(max = 255, message = "Treatment must be at most 255 characters")
    @Schema(description = "Optional free-text treatment administered.", example = "Antibiotics course, 5 days")
    private String treatment;

    @Size(max = 100, message = "Vet name must be at most 100 characters")
    @Schema(description = "Optional name of the attending veterinarian.", example = "Dr. Suresh Patil")
    private String vetName;
}
