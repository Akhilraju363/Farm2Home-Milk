package com.farm2home.order.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CartResponse {
    private UUID cartId;
    private List<CartItemResponse> items;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Sum of available items' subtotals only - " +
            "unavailable items (see CartItemResponse.available) don't count toward it.")
    private BigDecimal subtotal;
    private int itemCount;
}
