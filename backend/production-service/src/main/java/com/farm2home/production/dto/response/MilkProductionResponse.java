package com.farm2home.production.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MilkProductionResponse {
    private UUID id;
    private UUID cowId;
    private LocalDate collectionDate;
    private String session;
    private BigDecimal quantityLiters;
    private BigDecimal fatPercentage;
    private BigDecimal snfPercentage;
    private String qualityGrade;
    private String collectedBy;
    private String notes;
    private LocalDateTime createdAt;
    private String createdBy;
}
