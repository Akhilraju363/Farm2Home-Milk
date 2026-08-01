package com.farm2home.events.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Published to {@code inventory.events} whenever a stock transaction changes an item's quantity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryEvent {

    /** INVENTORY_UPDATED */
    private String eventType;
    private UUID itemId;
    private String itemName;
    private String itemType;
    private BigDecimal quantity;
    private BigDecimal previousQuantity;
    private String unit;
    private BigDecimal reorderLevel;
    private boolean lowStock;
    private LocalDateTime occurredAt;
}
