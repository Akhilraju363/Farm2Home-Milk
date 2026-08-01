package com.farm2home.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class GenerationResultResponse {

    @Schema(example = "2026-06-25")
    private LocalDate date;

    @Schema(description = "Orders newly created by this run", example = "42")
    private int ordersCreated;

    @Schema(description = "Total ACTIVE subscriptions evaluated", example = "50")
    private int subscriptionsProcessed;

    @Schema(description = "Subscriptions not due today, or an order already existed for this date "
            + "(the job is idempotent - re-running it for the same date never double-creates)", example = "8")
    private int skipped;

    @Schema(example = "Generated 42 orders for 2026-06-25")
    private String message;
}
