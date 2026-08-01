package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the Inventory Report (inventory-service) — stock transaction history. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReportRow {
    @Schema(example = "4d5e6f70-9c3f-4b2a-8e1d-3a4b5c6d7e8f")
    private UUID transactionId;
    @Schema(example = "1a2b3c4d-5e6f-4708-9c3f-b2a8e1d3a4b5")
    private UUID itemId;
    @Schema(example = "Cattle Feed - Premium")
    private String itemName;
    @Schema(description = "inventory-service TxnType enum name", example = "IN")
    private String txnType;
    @Schema(example = "50.00")
    private BigDecimal quantity;
    @Schema(example = "2026-07-20T14:30:00")
    private LocalDateTime transactedAt;
}
