package com.farm2home.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Rate/review a delivered order's product. customerId is never accepted here - "
        + "it is always taken from the caller's bearer token.")
public class CreateReviewRequest {

    @NotNull(message = "Order id is required")
    @Schema(description = "The delivered order this review is for.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID orderId;

    @NotNull(message = "Product id is required")
    @Schema(description = "The product being rated.", example = "9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f")
    private UUID productId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(description = "1 to 5 stars. Required.", example = "5")
    private Integer rating;

    @Size(max = 2000, message = "Review text must be at most 2000 characters")
    @Schema(description = "Optional free-text review.", example = "Fresh and delivered on time every day.")
    private String reviewText;
}
