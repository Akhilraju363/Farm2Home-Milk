package com.farm2home.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Computed server-side from every active review for the product (see
 *  ReviewRepository.ratingSummaryRaw) - never paginated, always the true aggregate. */
@Data
@Builder
public class RatingSummaryResponse {

    @Schema(description = "Product ID this summary is for.")
    private UUID productId;

    @Schema(description = "Average rating across all active reviews, rounded to 1 decimal place. "
            + "0 if there are no reviews yet.", example = "4.3")
    private BigDecimal averageRating;

    @Schema(description = "Total number of active reviews.", example = "27")
    private long totalReviews;

    @Schema(description = "Count of 1-star reviews.")
    private long rating1Count;

    @Schema(description = "Count of 2-star reviews.")
    private long rating2Count;

    @Schema(description = "Count of 3-star reviews.")
    private long rating3Count;

    @Schema(description = "Count of 4-star reviews.")
    private long rating4Count;

    @Schema(description = "Count of 5-star reviews.")
    private long rating5Count;
}
