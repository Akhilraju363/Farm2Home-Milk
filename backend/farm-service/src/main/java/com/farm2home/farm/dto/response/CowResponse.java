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
public class CowResponse {
    private UUID id;
    private String tagNumber;
    private String cowName;
    private String breed;
    private LocalDate dateOfBirth;
    private LocalDate purchaseDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
