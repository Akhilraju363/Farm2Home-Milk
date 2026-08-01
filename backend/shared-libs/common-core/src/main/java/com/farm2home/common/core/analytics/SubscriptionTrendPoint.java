package com.farm2home.common.core.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One point of the Subscription Trend (subscription-service) - new subscriptions started per period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionTrendPoint {

    @Schema(description = "Start of this period's bucket (day/week/month/year, per the requested granularity)",
            example = "2026-06-01")
    private LocalDate period;

    @Schema(description = "Subscriptions created within this period", example = "18")
    private long newSubscriptions;
}
