package com.farm2home.inventory.domain.enums;

/** Not persisted - derived from stockQuantity vs minimumStockQuantity at read time (see
 *  ProductMapper), the same "compute, don't store" pattern used elsewhere in this codebase for
 *  values that are pure functions of other columns. */
public enum ProductStockStatus {
    IN_STOCK, LOW_STOCK, OUT_OF_STOCK
}
