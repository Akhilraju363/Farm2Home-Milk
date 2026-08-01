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
public class InventoryReportSummary {
    @Schema(example = "312")
    private long totalTransactions;
    @Schema(description = "Sum of quantity across IN transactions matching the filters", example = "1840.00")
    private BigDecimal totalInQuantity;
    @Schema(description = "Sum of quantity across OUT transactions matching the filters", example = "1265.50")
    private BigDecimal totalOutQuantity;
}
