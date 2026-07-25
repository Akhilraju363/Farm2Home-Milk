package com.farm2home.farm.dto.request;

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
    private String vaccineName;

    @NotNull(message = "Administered date is required")
    @PastOrPresent(message = "Administered date cannot be in the future")
    private LocalDate administeredAt;

    private LocalDate nextDueDate;
    @Size(max = 100, message = "Administered by must be at most 100 characters")
    private String administeredBy;
    @Size(max = 255, message = "Notes must be at most 255 characters")
    private String notes;
}
