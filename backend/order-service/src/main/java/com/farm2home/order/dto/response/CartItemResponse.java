package com.farm2home.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CartItemResponse {
    @Schema(description = "CartItem id - use this for PUT/DELETE .../cart/items/{id}.")
    private UUID id;
    private UUID productId;
    @Schema(description = "Live product name, name/image/price resolved at read time - null if the product " +
            "was deleted after being added to the cart (see available below).")
    private String productName;
    private String imageUrl;
    private BigDecimal quantity;
    private String unit;
    @Schema(description = "Current live product price - never a stored snapshot, since checkout resolves the " +
            "real price again anyway.")
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    @Schema(description = "False if the product is no longer active/available/found - this line is excluded " +
            "from the cart's subtotal and cannot be checked out until removed or the product returns.")
    private boolean available;
    @Schema(description = "Present only when available is false, e.g. \"This product is no longer available.\"")
    private String unavailableReason;
}
