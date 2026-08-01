package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryReportSummary {
    @Schema(example = "410")
    private long totalDeliveries;
    @Schema(description = "Count with status DELIVERED", example = "375")
    private long completedCount;
    @Schema(description = "Count with status FAILED", example = "12")
    private long failedCount;
}
