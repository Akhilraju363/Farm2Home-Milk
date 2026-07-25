package com.farm2home.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopUpWalletRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum top-up amount is 1")
    private BigDecimal amount;

    @jakarta.validation.constraints.Size(max = 255, message = "Description must be at most 255 characters")
    private String description;
}
