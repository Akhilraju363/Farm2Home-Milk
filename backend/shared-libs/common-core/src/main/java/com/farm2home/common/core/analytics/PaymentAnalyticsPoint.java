package com.farm2home.common.core.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One point of Payment Analytics (payment-service) - transaction volume and outcome mix per
 *  period, independent of payment status (unlike Revenue Trend, which only counts SUCCESS). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAnalyticsPoint {
    private LocalDate period;
    private long totalPayments;
    private BigDecimal totalAmount;
    private long successCount;
    private long failedCount;
}
