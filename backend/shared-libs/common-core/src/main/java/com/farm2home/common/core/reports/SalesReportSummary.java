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
public class SalesReportSummary {

    @Schema(description = "Count of orders matching the report's filters", example = "128")
    private long totalOrders;

    @Schema(description = "Sum of totalAmount across the matching orders, in rupees", example = "15360.00")
    private BigDecimal totalRevenue;
}
