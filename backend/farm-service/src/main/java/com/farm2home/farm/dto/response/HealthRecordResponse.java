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
public class HealthRecordResponse {
    private UUID id;
    private UUID cowId;
    private String tagNumber;
    private LocalDate recordDate;
    private String condition;
    private String symptoms;
    private String treatment;
    private String vetName;
    private LocalDateTime createdAt;
}
