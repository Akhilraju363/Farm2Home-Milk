package com.farm2home.order.client;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Minimal local projection of inventory-service's real ProductResponse - only the fields order
 *  creation/cart display needs to price/validate/render a product-based order item. unit and
 *  imageUrl are display-only (Cart's response), not used by order-creation validation. Mirrors
 *  the same pattern as invoice-service's client DTOs (see OrderDetailResponse there). */
@Data
public class ProductDetailResponse {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String unit;
    private String imageUrl;
    private boolean active;
    private boolean availability;
    private Integer stockQuantity;
}
