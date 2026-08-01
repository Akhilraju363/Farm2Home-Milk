package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Partial update - any field left null is left unchanged on the existing product. "
        + "Set active=false to deactivate/hide the product.")
public class UpdateProductRequest {

    @Size(max = ValidationConstants.NAME_MAX_LENGTH, message = "Product name must be at most 100 characters")
    @Schema(description = "New product name. Omit/null to leave unchanged. Max 100 characters.",
            example = "Full Cream Milk 1L")
    private String name;

    @Schema(description = "New description. Omit/null to leave unchanged.",
            example = "Farm-fresh full cream milk, pasteurized")
    private String description;

    @Size(max = ValidationConstants.SHORT_NAME_MAX_LENGTH, message = "Category must be at most 50 characters")
    @Schema(description = "New category. Omit/null to leave unchanged. Max 50 characters.", example = "Milk")
    private String category;

    @DecimalMin(value = ValidationConstants.MIN_ZERO, inclusive = false, message = "Price must be greater than zero")
    @Schema(description = "New selling price, in rupees. Omit/null to leave unchanged. Must be greater than zero.",
            example = "65.00")
    private BigDecimal price;

    @Schema(description = "Active status. Omit/null to leave unchanged; false deactivates the product.", example = "true")
    private Boolean active;
}
