package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.inventory.domain.enums.ProductUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Partial update - any field left null is left unchanged on the existing product. "
        + "Set active=false to deactivate/hide the product. There is no way to clear an already-set "
        + "category via this endpoint (null means \"leave unchanged\", same as every other field here).")
public class UpdateProductRequest {

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Product name must be at most 100 characters")
    @Schema(description = "New product name. Omit/null to leave unchanged. Max 100 characters.",
            example = "Full Cream Milk 1L")
    private String name;

    @Schema(description = "New description. Omit/null to leave unchanged.",
            example = "Farm-fresh full cream milk, pasteurized")
    private String description;

    @Schema(description = "New category ID. Omit/null to leave unchanged. If provided, must reference "
            + "a category that exists and is active.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID categoryId;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, inclusive = false, message = "Price must be greater than zero")
    @Schema(description = "New selling price, in rupees. Omit/null to leave unchanged. Must be greater than zero.",
            example = "65.00")
    private BigDecimal price;

    @Schema(description = "New unit of sale. Omit/null to leave unchanged.", example = "L")
    private ProductUnit unit;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Schema(description = "New stock quantity. Omit/null to leave unchanged.", example = "142")
    private Integer stockQuantity;

    @Min(value = 0, message = "Minimum stock quantity cannot be negative")
    @Schema(description = "New reorder threshold. Omit/null to leave unchanged.", example = "20")
    private Integer minimumStockQuantity;

    @Schema(description = "Active status. Omit/null to leave unchanged; false deactivates the product.", example = "true")
    private Boolean active;
}
