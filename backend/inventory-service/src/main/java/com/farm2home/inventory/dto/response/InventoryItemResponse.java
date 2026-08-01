package com.farm2home.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryItemResponse {

    @Schema(description = "Inventory item ID.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID id;

    @Schema(description = "Item name.", example = "Cattle Feed - Premium Mix")
    private String itemName;

    @Schema(description = "Item type.", example = "FEED")
    private String itemType;

    @Schema(description = "Current stock quantity. Only ever changed via stock transactions.", example = "250.00")
    private BigDecimal quantity;

    @Schema(description = "Unit of measure.", example = "KG")
    private String unit;

    @Schema(description = "Reorder threshold.", example = "50.00")
    private BigDecimal reorderLevel;

    @Schema(description = "True when quantity <= reorderLevel - i.e. this item is currently low-stock.",
            example = "false")
    private boolean belowReorderLevel;

    @Schema(description = "Price per unit, in rupees. Null if not set.", example = "32.50")
    private BigDecimal unitPrice;

    @Schema(description = "Supplier name. Null if not set.", example = "Green Valley Suppliers")
    private String supplier;

    @Schema(description = "When the item was created.", example = "2026-07-01T09:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Mobile number/identifier of the user who created the item.", example = "9876543210")
    private String createdBy;
}
