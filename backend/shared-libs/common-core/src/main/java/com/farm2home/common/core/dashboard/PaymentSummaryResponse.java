package com.farm2home.common.core.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Shared response contract for GET /api/v1/payments/summary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryResponse {
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
}
