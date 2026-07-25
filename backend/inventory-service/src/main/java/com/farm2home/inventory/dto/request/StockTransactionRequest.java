package com.farm2home.inventory.dto.request;

import com.farm2home.inventory.domain.enums.TxnType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class StockTransactionRequest {

    @NotNull(message = "Transaction type is required")
    private TxnType txnType;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Quantity must have up to 8 digits before and 2 digits after the decimal point")
    private BigDecimal quantity;

    @Size(max = 100, message = "Reason must be at most 100 characters")
    private String reason;

    private UUID referenceId;
}
