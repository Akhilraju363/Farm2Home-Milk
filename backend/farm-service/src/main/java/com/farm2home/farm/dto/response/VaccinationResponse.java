package com.farm2home.farm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VaccinationResponse {
    private UUID id;
    private UUID cowId;
    private String tagNumber;
    private String vaccineName;
    private LocalDate administeredAt;
    private LocalDate nextDueDate;
    private String administeredBy;
    private String notes;
    private LocalDateTime createdAt;
}
