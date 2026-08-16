package com.farm2home.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AddCartItemRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "A real Product id from GET /inventory/products.",
            example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID productId;

    // If the product is already in the cart, this is ADDED to the existing quantity rather than
    // replacing it (see CartServiceImpl.addItem) - PUT /cart/items/{id} is the absolute-set path.
    @NotNull(message = "Quantity is required")
    @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;
}
