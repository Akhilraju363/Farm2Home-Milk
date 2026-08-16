package com.farm2home.order.dto.request;

import com.farm2home.order.domain.enums.MilkType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single item within a manual order - specify exactly one of milkType "
        + "(legacy, quantity in litres) or productId (a real inventory-service catalog product, "
        + "quantity in that product's own unit). Specifying both, or neither, is rejected.")
public class CreateOrderItemRequest {

    @Schema(example = "FULL_CREAM", description = "Legacy milk order - mutually exclusive with productId.")
    private MilkType milkType;

    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
            description = "A real Product id from GET /inventory/products - mutually exclusive with milkType. "
                    + "Price is always resolved server-side from the product's current price, never accepted "
                    + "from the client.")
    private UUID productId;

    // Bounds differ by which of milkType/productId is set (litres vs. the product's own unit) and
    // can't both be expressed as one static @DecimalMin/@DecimalMax pair - see
    // OrderServiceImpl.validateQuantity, which enforces the right range for whichever was sent.
    @NotNull(message = "Quantity is required")
    @Schema(example = "1.5")
    private BigDecimal quantity;
}
