package com.farm2home.common.core.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shared response contract for GET /api/v1/orders/summary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryResponse {

    @Schema(description = "Orders placed today, platform-wide", example = "56")
    private long todaysOrders;

    @Schema(description = "Orders currently in PENDING status, platform-wide", example = "12")
    private long pendingOrders;
}
