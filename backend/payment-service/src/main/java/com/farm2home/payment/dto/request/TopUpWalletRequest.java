package com.farm2home.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Credit the caller's own wallet by a fixed amount")
public class TopUpWalletRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum top-up amount is 1")
    @Schema(description = "Amount to credit, in rupees. Must be at least 1.00.", example = "500.00")
    private BigDecimal amount;

    @jakarta.validation.constraints.Size(max = 255, message = "Description must be at most 255 characters")
    @Schema(description = "Optional free-text note for the resulting wallet transaction. Max 255 characters.",
            example = "Added via UPI")
    private String description;
}
