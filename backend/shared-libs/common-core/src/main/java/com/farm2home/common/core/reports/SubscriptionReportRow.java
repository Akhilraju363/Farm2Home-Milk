package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the Subscription Report (subscription-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionReportRow {

    @Schema(example = "9c3f2b1a-4d5e-4f6a-8b7c-1a2b3c4d5e6f")
    private UUID subscriptionId;

    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;

    @Schema(example = "FULL_CREAM")
    private String milkType;

    @Schema(description = "Litres per delivery", example = "1.50")
    private BigDecimal quantity;

    @Schema(description = "DAILY, ALTERNATE_DAY, or WEEKLY", example = "DAILY")
    private String scheduleType;

    @Schema(example = "2026-06-01")
    private LocalDate startDate;

    @Schema(description = "Null for an open-ended subscription", example = "2026-12-31")
    private LocalDate endDate;

    @Schema(description = "ACTIVE, PAUSED, CANCELLED, or EXPIRED", example = "ACTIVE")
    private String status;
}
