package com.farm2home.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateReviewRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(description = "1 to 5 stars. Required.", example = "4")
    private Integer rating;

    @Size(max = 2000, message = "Review text must be at most 2000 characters")
    @Schema(description = "Optional free-text review.", example = "Updated my thoughts after a week of deliveries.")
    private String reviewText;
}
