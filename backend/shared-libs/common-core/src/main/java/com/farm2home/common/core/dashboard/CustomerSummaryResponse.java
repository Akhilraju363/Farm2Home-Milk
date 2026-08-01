package com.farm2home.common.core.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared response contract for GET /api/v1/customers/summary, defined once here so
 * customer-service (producer) and dashboard-service (consumer) deserialize the exact same
 * shape instead of each declaring their own copy of this DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSummaryResponse {
    private long totalCustomers;
}
