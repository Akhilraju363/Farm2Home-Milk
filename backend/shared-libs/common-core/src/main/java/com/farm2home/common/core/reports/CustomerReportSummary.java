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
public class CustomerReportSummary {
    @Schema(example = "128")
    private long totalCustomers;
    @Schema(example = "104")
    private long activeCustomers;
    @Schema(example = "18")
    private long inactiveCustomers;
    @Schema(example = "6")
    private long suspendedCustomers;
}
