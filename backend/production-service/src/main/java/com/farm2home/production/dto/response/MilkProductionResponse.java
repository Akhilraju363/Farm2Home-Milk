package com.farm2home.production.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MilkProductionResponse {

    @Schema(description = "Milk production record ID.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID id;

    @Schema(description = "ID of the cow this collection is for.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID cowId;

    @Schema(description = "Date the milk was collected.", example = "2026-07-31")
    private LocalDate collectionDate;

    @Schema(description = "Collection session.", example = "MORNING")
    private String session;

    @Schema(description = "Milk collected, in liters.", example = "12.50")
    private BigDecimal quantityLiters;

    @Schema(description = "Fat content percentage. Null if not recorded.", example = "4.20")
    private BigDecimal fatPercentage;

    @Schema(description = "Solids-not-fat content percentage. Null if not recorded.", example = "8.60")
    private BigDecimal snfPercentage;

    @Schema(description = "Overall quality grade. Null if not recorded.", example = "A")
    private String qualityGrade;

    @Schema(description = "Name of the person who collected the milk. Null if not recorded.", example = "Ramesh Kumar")
    private String collectedBy;

    @Schema(description = "Free-text notes. Null if not recorded.", example = "Normal collection")
    private String notes;

    @Schema(description = "When the record was created.", example = "2026-07-31T06:15:00")
    private LocalDateTime createdAt;

    @Schema(description = "Mobile number/identifier of the user who created the record.", example = "9876543210")
    private String createdBy;
}
