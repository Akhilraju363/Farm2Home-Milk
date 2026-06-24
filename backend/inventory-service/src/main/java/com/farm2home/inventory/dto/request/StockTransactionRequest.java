package com.farm2home.inventory.dto.request;

import com.farm2home.inventory.domain.enums.TxnType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class StockTransactionRequest {

    @NotNull
    private TxnType txnType;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal quantity;

    @Size(max = 100)
    private String reason;

    private UUID referenceId;
}
