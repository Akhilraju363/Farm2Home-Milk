package com.farm2home.common.core.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of {@link InventorySummaryResponse#getTopItems()}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockItemSummary {
    private UUID id;
    private String name;
    private BigDecimal quantity;
    private BigDecimal reorderLevel;
}
