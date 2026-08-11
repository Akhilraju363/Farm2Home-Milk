package com.farm2home.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProductCategoryResponse {

    @Schema(description = "Category ID.", example = "9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f")
    private UUID id;

    @Schema(description = "Category name.", example = "Milk")
    private String name;

    @Schema(description = "Category description. Null if not set.", example = "Fresh dairy milk in all pack sizes")
    private String description;

    @Schema(description = "Whether the category currently accepts new/updated product assignments.", example = "true")
    private boolean active;

    @Schema(description = "When the category was created.", example = "2026-06-01T08:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "When the category was last updated.", example = "2026-06-15T10:00:00")
    private LocalDateTime updatedAt;
}
