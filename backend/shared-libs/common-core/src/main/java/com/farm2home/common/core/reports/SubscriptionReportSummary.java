package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionReportSummary {

    @Schema(description = "Count of subscriptions matching the report's filters", example = "340")
    private long totalSubscriptions;

    @Schema(description = "Of those, how many are currently ACTIVE", example = "290")
    private long activeSubscriptions;

    @Schema(description = "Sum of quantity across the matching subscriptions, in litres", example = "512.50")
    private BigDecimal totalQuantity;
}
