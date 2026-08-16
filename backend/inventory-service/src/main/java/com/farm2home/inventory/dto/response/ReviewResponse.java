package com.farm2home.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {

    @Schema(description = "Review ID.")
    private UUID id;

    @Schema(description = "Reviewed product's ID.")
    private UUID productId;

    @Schema(description = "Reviewed product's name, resolved for display.")
    private String productName;

    @Schema(description = "Reviewing customer's ID. Only present to the review's own author or an admin.")
    private UUID customerId;

    @Schema(description = "Reviewing customer's display name (first name + last initial), resolved for "
            + "display on the product's public review list. Null if the customer record could not be resolved.",
            example = "Akhil R.")
    private String customerDisplayName;

    @Schema(description = "The delivered order this review is for.")
    private UUID orderId;

    @Schema(description = "1 to 5 stars.")
    private Integer rating;

    @Schema(description = "Free-text review. Null if none was given.")
    private String reviewText;

    @Schema(description = "When the review was created.")
    private LocalDateTime createdAt;

    @Schema(description = "When the review was last edited.")
    private LocalDateTime updatedAt;
}
