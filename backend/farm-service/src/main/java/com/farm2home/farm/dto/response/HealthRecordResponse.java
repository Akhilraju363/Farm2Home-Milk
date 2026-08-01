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
public class HealthRecordResponse {

    @Schema(description = "Health record id.", example = "6a9f1e2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b")
    private UUID id;

    @Schema(description = "Id of the cow this record belongs to.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID cowId;

    @Schema(description = "Tag number of the cow this record belongs to, denormalized for display.", example = "TAG001")
    private String tagNumber;

    @Schema(description = "Date the observation/treatment took place.", example = "2026-07-28")
    private LocalDate recordDate;

    @Schema(description = "Cow's health condition at the time of this record.", example = "SICK")
    private String condition;

    @Schema(description = "Symptoms observed, if recorded.", example = "Reduced appetite, mild fever")
    private String symptoms;

    @Schema(description = "Treatment administered, if recorded.", example = "Antibiotics course, 5 days")
    private String treatment;

    @Schema(description = "Attending veterinarian, if recorded.", example = "Dr. Suresh Patil")
    private String vetName;

    @Schema(description = "Record creation timestamp.", example = "2026-07-28T11:15:00")
    private LocalDateTime createdAt;
}
