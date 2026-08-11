package com.farm2home.inventory.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "New categories are active by default; there is no active flag here - "
        + "deactivate via the update endpoint after creation.")
public class CreateProductCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = ValidationConstants.SHORT_NAME_MAX_LENGTH, message = "Category name must be at most 50 characters")
    @Schema(description = "Category name. Required, must be unique (case-insensitive), max 50 characters.",
            example = "Milk")
    private String name;

    @Schema(description = "Free-text category description. Optional.", example = "Fresh dairy milk in all pack sizes")
    private String description;
}
