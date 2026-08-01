package com.farm2home.common.core.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One point of the Revenue Trend (payment-service) - sum of SUCCESS payment amounts per period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTrendPoint {
    private LocalDate period;
    private BigDecimal revenue;
}
