package com.farm2home.common.core.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One point of the Customer Growth trend (customer-service) - new customer signups per period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerGrowthPoint {
    private LocalDate period;
    private long newCustomers;
}
