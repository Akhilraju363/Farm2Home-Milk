package com.farm2home.farm.dto.request;

import com.farm2home.farm.domain.enums.HealthCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateHealthRecordRequest {

    @NotNull(message = "Record date is required")
    @PastOrPresent(message = "Record date cannot be in the future")
    private LocalDate recordDate;

    @NotNull(message = "Condition is required")
    private HealthCondition condition;

    @Size(max = 255, message = "Symptoms must be at most 255 characters")
    private String symptoms;
    @Size(max = 255, message = "Treatment must be at most 255 characters")
    private String treatment;
    @Size(max = 100, message = "Vet name must be at most 100 characters")
    private String vetName;
}
