package com.farm2home.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCartItemRequest {

    // Absolute, not a delta - matches how the Cart page's quantity stepper reads.
    @NotNull(message = "Quantity is required")
    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;
}
