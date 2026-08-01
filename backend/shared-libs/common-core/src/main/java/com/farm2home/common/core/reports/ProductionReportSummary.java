package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionReportSummary {
    @Schema(example = "640")
    private long totalRecords;
    @Schema(description = "Sum of quantityLiters across the matching records", example = "8025.50")
    private BigDecimal totalLiters;
}
