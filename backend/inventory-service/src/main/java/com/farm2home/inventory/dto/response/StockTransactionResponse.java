package com.farm2home.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StockTransactionResponse {

    @Schema(description = "Transaction ID.", example = "5e2c1a3b-6d4f-4a8b-9e1c-2f3a4b5c6d7e")
    private UUID id;

    @Schema(description = "ID of the inventory item this transaction was applied to.",
            example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID itemId;

    @Schema(description = "Name of the inventory item at the time of the transaction.", example = "Cattle Feed - Premium Mix")
    private String itemName;

    @Schema(description = "IN (added to stock) or OUT (removed from stock).", example = "OUT")
    private String txnType;

    @Schema(description = "Quantity moved, in the item's unit.", example = "20.00")
    private BigDecimal quantity;

    @Schema(description = "Free-text reason for the transaction. Null if not provided.", example = "Weekly feed restock")
    private String reason;

    @Schema(description = "Optional external reference ID. Null if not provided.",
            example = "1f9c2b3a-4d5e-6f70-8192-a3b4c5d6e7f8")
    private UUID referenceId;

    @Schema(description = "When the transaction was recorded.", example = "2026-07-31T10:20:00")
    private LocalDateTime transactedAt;

    @Schema(description = "Mobile number/identifier of the user who recorded the transaction.", example = "9876543210")
    private String createdBy;
}
