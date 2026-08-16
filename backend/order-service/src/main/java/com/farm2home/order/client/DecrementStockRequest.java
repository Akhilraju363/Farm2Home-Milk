package com.farm2home.order.client;

import lombok.Data;

/** Local projection of inventory-service's real DecrementStockRequest - just the one field it
 *  needs. */
@Data
public class DecrementStockRequest {
    private Integer quantity;
}
