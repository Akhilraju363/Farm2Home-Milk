package com.farm2home.common.core.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Shared response contract for GET /api/v1/productions/summary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionSummaryResponse {
    private BigDecimal totalLitersToday;
}
