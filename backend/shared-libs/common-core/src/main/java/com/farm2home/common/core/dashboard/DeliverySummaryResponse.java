package com.farm2home.common.core.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shared response contract for GET /api/v1/delivery/assignments/summary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySummaryResponse {
    private long completedDeliveriesToday;
}
