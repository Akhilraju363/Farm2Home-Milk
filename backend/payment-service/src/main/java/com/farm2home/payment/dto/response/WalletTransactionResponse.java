package com.farm2home.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletTransactionResponse {
    private UUID id;
    private String transactionType;
    private BigDecimal amount;
    private UUID referenceId;
    private String description;
    private LocalDateTime createdAt;
}
