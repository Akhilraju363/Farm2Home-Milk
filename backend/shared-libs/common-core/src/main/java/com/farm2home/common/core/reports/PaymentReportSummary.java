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
public class PaymentReportSummary {
    @Schema(example = "512")
    private long totalPayments;
    @Schema(description = "Sum of amount across all matching payments, regardless of status", example = "72400.00")
    private BigDecimal totalAmount;
    @Schema(description = "Sum of amount across matching payments with status SUCCESS", example = "68950.00")
    private BigDecimal successAmount;
}
