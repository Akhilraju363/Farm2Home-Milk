package com.farm2home.production.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor
public class DailySummaryResponse {

    @Schema(description = "Collection date this summary row aggregates.", example = "2026-07-31")
    private LocalDate date;

    @Schema(description = "Total liters collected on this date.", example = "145.50")
    private BigDecimal totalLiters;

    @Schema(description = "Number of production records on this date.", example = "12")
    private long recordCount;
}
