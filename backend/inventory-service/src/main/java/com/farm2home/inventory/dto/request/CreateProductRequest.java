package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.inventory.domain.enums.ProductUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "New products are active by default; there is no active flag here - deactivate via "
        + "the update endpoint after creation.")
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Product name must be at most 100 characters")
    @Schema(description = "Product name. Required, max 100 characters.", example = "Full Cream Milk 1L")
    private String name;

    @Schema(description = "Free-text product description. Optional.",
            example = "Farm-fresh full cream milk, pasteurized")
    private String description;

    @Schema(description = "ID of an existing, active ProductCategory. Optional - a product may be "
            + "uncategorized. If provided, must reference a category that exists and is active.",
            example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID categoryId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = ValidationConstants.MIN_ZERO, inclusive = false, message = "Price must be greater than zero")
    @Schema(description = "Selling price, in rupees. Required, must be greater than zero.", example = "65.00")
    private BigDecimal price;

    @NotNull(message = "Unit is required")
    @Schema(description = "Unit of sale. Required.", example = "L")
    private ProductUnit unit;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Schema(description = "Current stock quantity. Optional, defaults to 0.", example = "142")
    private Integer stockQuantity;

    @Min(value = 0, message = "Minimum stock quantity cannot be negative")
    @Schema(description = "Reorder threshold - stockQuantity at or below this is LOW_STOCK. "
            + "Optional, defaults to 0.", example = "20")
    private Integer minimumStockQuantity;
}
