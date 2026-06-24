package com.farm2home.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryItemResponse {
    private UUID id;
    private String itemName;
    private String itemType;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal reorderLevel;
    private boolean belowReorderLevel;
    private BigDecimal unitPrice;
    private String supplier;
    private LocalDateTime createdAt;
    private String createdBy;
}
