package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

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

    @Size(max = ValidationConstants.SHORT_NAME_MAX_LENGTH, message = "Category must be at most 50 characters")
    @Schema(description = "Product category. Optional, max 50 characters.", example = "Milk")
    private String category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = ValidationConstants.MIN_ZERO, inclusive = false, message = "Price must be greater than zero")
    @Schema(description = "Selling price, in rupees. Required, must be greater than zero.", example = "65.00")
    private BigDecimal price;
}
