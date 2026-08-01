package com.farm2home.common.core.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shared response contract for GET /api/v1/subscriptions/summary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSummaryResponse {

    @Schema(description = "Total subscriptions currently in ACTIVE status, platform-wide", example = "290")
    private long activeSubscriptions;
}
