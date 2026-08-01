package com.farm2home.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderItemResponse {

    @Schema(example = "1f2e3d4c-5b6a-4978-8f7e-6d5c4b3a2f1e")
    private UUID id;

    @Schema(example = "FULL_CREAM")
    private String milkType;

    @Schema(description = "Quantity in litres", example = "2.00")
    private BigDecimal quantity;

    @Schema(description = "Price per litre in rupees, snapshotted at order time", example = "60.00")
    private BigDecimal unitPrice;

    @Schema(description = "quantity × unitPrice", example = "120.00")
    private BigDecimal totalPrice;
}
