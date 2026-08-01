package com.farm2home.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletTransactionResponse {

    @Schema(example = "9c3f2b1a-4d5e-4f6a-8b7c-1a2b3c4d5e6f")
    private UUID id;

    @Schema(description = "CREDIT (top-up, refund) or DEBIT (payment)", example = "CREDIT")
    private String transactionType;

    @Schema(description = "Transaction amount in rupees", example = "500.00")
    private BigDecimal amount;

    @Schema(description = "The payment or refund this transaction is linked to, if any", example = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e")
    private UUID referenceId;

    @Schema(example = "Added via UPI")
    private String description;

    @Schema(example = "2026-07-31T10:15:30")
    private LocalDateTime createdAt;
}
