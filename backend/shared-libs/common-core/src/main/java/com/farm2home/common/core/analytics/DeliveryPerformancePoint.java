package com.farm2home.common.core.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One point of the Delivery Performance trend (delivery-service) - delivery outcome mix per period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPerformancePoint {
    private LocalDate period;
    private long totalDeliveries;
    private long completedDeliveries;
    private long failedDeliveries;
}
