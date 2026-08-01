package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.inventory.domain.enums.TxnType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class StockTransactionRequest {

    @NotNull(message = "Transaction type is required")
    @Schema(description = "IN adds to the item's quantity, OUT subtracts from it. Required. An OUT that would "
            + "take the item's quantity below zero is rejected.", example = "OUT")
    private TxnType txnType;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = ValidationConstants.MIN_POSITIVE_AMOUNT, message = "Quantity must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Quantity must have up to 8 digits before and 2 digits after the decimal point")
    @Schema(description = "Quantity moved, in the item's unit. Required, must be greater than zero.", example = "20.00")
    private BigDecimal quantity;

    @Size(max = 100, message = "Reason must be at most 100 characters")
    @Schema(description = "Free-text reason for the transaction. Optional, max 100 characters.",
            example = "Weekly feed restock")
    private String reason;

    @Schema(description = "Optional external reference ID this transaction relates to (e.g. a purchase order "
            + "or production batch UUID). Not validated against any other entity.",
            example = "1f9c2b3a-4d5e-6f70-8192-a3b4c5d6e7f8")
    private UUID referenceId;
}
