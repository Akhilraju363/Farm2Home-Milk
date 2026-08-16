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

    @Schema(example = "FULL_CREAM", description = "Null for a product-based item - see productId.")
    private String milkType;

    @Schema(description = "Null for a milkType-based item.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID productId;

    @Schema(description = "Snapshotted at order time - stays accurate even if the product is "
            + "later renamed. Null for a milkType-based item.", example = "Full Cream Milk 1L")
    private String productName;

    @Schema(description = "Quantity in litres (milkType) or the product's own unit (productId)", example = "2.00")
    private BigDecimal quantity;

    @Schema(description = "Price per litre in rupees, snapshotted at order time", example = "60.00")
    private BigDecimal unitPrice;

    @Schema(description = "quantity × unitPrice", example = "120.00")
    private BigDecimal totalPrice;
}
