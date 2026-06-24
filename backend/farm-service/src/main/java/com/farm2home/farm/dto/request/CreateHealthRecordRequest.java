package com.farm2home.farm.dto.request;

import com.farm2home.farm.domain.enums.HealthCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateHealthRecordRequest {

    @NotNull(message = "Record date is required")
    @PastOrPresent(message = "Record date cannot be in the future")
    private LocalDate recordDate;

    @NotNull(message = "Condition is required")
    private HealthCondition condition;

    private String symptoms;
    private String treatment;
    private String vetName;
}
