package com.farm2home.order.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.order.domain.enums.MilkType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single item within a manual order")
public class CreateOrderItemRequest {

    @NotNull(message = "Milk type is required")
    @Schema(example = "FULL_CREAM")
    private MilkType milkType;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = ValidationConstants.MIN_SUBSCRIPTION_QUANTITY, message = "Minimum quantity is 0.5 litres")
    @DecimalMax(value = "10.0", message = "Maximum quantity is 10 litres")
    @Schema(example = "1.5")
    private BigDecimal quantity;
}
