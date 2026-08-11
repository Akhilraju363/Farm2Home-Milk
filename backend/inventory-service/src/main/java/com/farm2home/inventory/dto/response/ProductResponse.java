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

    @Schema(description = "Category ID. Null if the product is uncategorized.",
            example = "9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f")
    private UUID categoryId;

    @Schema(description = "Category name, resolved for display. Null if the product is uncategorized.",
            example = "Milk")
    private String categoryName;

    @Schema(description = "Selling price, in rupees.", example = "65.00")
    private BigDecimal price;

    @Schema(description = "Unit of sale.", example = "L")
    private String unit;

    @Schema(description = "Current stock quantity.", example = "142")
    private Integer stockQuantity;

    @Schema(description = "Reorder threshold used to derive stockStatus.", example = "20")
    private Integer minimumStockQuantity;

    @Schema(description = "Derived from stockQuantity vs minimumStockQuantity - not stored. "
            + "OUT_OF_STOCK if stockQuantity is 0, LOW_STOCK if at or below minimumStockQuantity, "
            + "otherwise IN_STOCK.", example = "IN_STOCK")
    private String stockStatus;

    @Schema(description = "Derived: true only when the product is both active and in stock - a product "
            + "can be active (not discontinued) yet still unavailable to order right now.", example = "true")
    private boolean availability;

    @Schema(description = "Relative URL of the uploaded product image. Null until an image is uploaded.",
            example = "/uploads/products/3b1e6a2c-full-cream-1l.jpg")
    private String imageUrl;

    @Schema(description = "Whether the product is currently active/visible to customers.", example = "true")
    private boolean active;

    @Schema(description = "When the product was created.", example = "2026-06-01T08:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "When the product was last updated.", example = "2026-06-15T10:00:00")
    private LocalDateTime updatedAt;
}
