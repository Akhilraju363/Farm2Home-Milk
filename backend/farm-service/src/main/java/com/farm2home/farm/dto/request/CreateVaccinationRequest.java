package com.farm2home.farm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateVaccinationRequest {

    @NotBlank(message = "Vaccine name is required")
    private String vaccineName;

    @NotNull(message = "Administered date is required")
    @PastOrPresent(message = "Administered date cannot be in the future")
    private LocalDate administeredAt;

    private LocalDate nextDueDate;
    private String administeredBy;
    private String notes;
}
