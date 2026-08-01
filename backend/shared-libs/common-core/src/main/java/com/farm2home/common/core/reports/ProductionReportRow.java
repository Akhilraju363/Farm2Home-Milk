package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the Production Report (production-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionReportRow {
    @Schema(example = "5e6f7089-c3f2-4b1a-8d3a-4b5c6d7e8f90")
    private UUID productionId;
    @Schema(example = "7089c3f2-1a4b-4d3a-8b5c-6d7e8f90a1b2")
    private UUID cowId;
    @Schema(example = "2026-07-20")
    private LocalDate collectionDate;
    @Schema(description = "MORNING or EVENING", example = "MORNING")
    private String session;
    @Schema(example = "12.50")
    private BigDecimal quantityLiters;
    @Schema(description = "production-service QualityGrade enum name", example = "A")
    private String qualityGrade;
}
