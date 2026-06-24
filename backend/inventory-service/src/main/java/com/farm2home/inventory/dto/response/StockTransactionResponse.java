package com.farm2home.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StockTransactionResponse {
    private UUID id;
    private UUID itemId;
    private String itemName;
    private String txnType;
    private BigDecimal quantity;
    private String reason;
    private UUID referenceId;
    private LocalDateTime transactedAt;
    private String createdBy;
}
