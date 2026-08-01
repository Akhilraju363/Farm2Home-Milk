package com.farm2home.common.core.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One point of the Inventory Consumption trend (inventory-service) - total OUT-transaction
 *  quantity per period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryConsumptionPoint {
    private LocalDate period;
    private BigDecimal consumedQuantity;
}
