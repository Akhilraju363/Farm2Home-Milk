package com.farm2home.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProductResponse {

    @Schema(description = "Product ID.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;

    @Schema(description = "Product name.", example = "Full Cream Milk 1L")
    private String name;

    @Schema(description = "Product description. Null if not set.", example = "Farm-fresh full cream milk, pasteurized")
    private String description;

    @Schema(description = "Product category. Null if not set.", example = "Milk")
    private String category;

    @Schema(description = "Selling price, in rupees.", example = "65.00")
    private BigDecimal price;

    @Schema(description = "Relative URL of the uploaded product image. Null until an image is uploaded.",
            example = "/uploads/products/3b1e6a2c-full-cream-1l.jpg")
    private String imageUrl;

    @Schema(description = "Whether the product is currently active/visible to customers.", example = "true")
    private boolean active;

    @Schema(description = "When the product was created.", example = "2026-06-01T08:30:00")
    private LocalDateTime createdAt;
}
