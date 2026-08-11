package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Partial update - any field left null is left unchanged on the existing category. "
        + "Set active=false to deactivate the category (existing products keep their reference; new/updated "
        + "products can no longer be assigned to it).")
public class UpdateProductCategoryRequest {

    @Size(max = ValidationConstants.SHORT_NAME_MAX_LENGTH, message = "Category name must be at most 50 characters")
    @Schema(description = "New category name. Omit/null to leave unchanged. Must be unique (case-insensitive).",
            example = "Milk")
    private String name;

    @Schema(description = "New description. Omit/null to leave unchanged.", example = "Fresh dairy milk in all pack sizes")
    private String description;

    @Schema(description = "Active status. Omit/null to leave unchanged; false deactivates the category.", example = "true")
    private Boolean active;
}
