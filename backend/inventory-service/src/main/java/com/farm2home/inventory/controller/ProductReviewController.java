package com.farm2home.inventory.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.dto.response.RatingSummaryResponse;
import com.farm2home.inventory.dto.response.ReviewResponse;
import com.farm2home.inventory.service.impl.ReviewServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A product's public review list and rating summary - kept as a separate controller from
 *  ProductController (which owns catalog CRUD) but nested under the same /products/{id} path,
 *  matching customer-service's CustomerController.../{id}/addresses nesting convention. Also
 *  doubles as the admin review-management view (SUPER_ADMIN/FARM_MANAGER read the same list;
 *  moderation is the DELETE on ReviewController). */
@RestController
@RequestMapping("/api/v1/inventory/products/{productId}")
@Tag(name = "Product Reviews", description = "Read-only: a product's reviews and aggregate rating.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ReviewServiceImpl service;

    @GetMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List a product's reviews",
            description = "Paginated, most-recent-first by default. Includes every active review for the "
                    + "product, not just from the caller.")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> findByProduct(
            @PathVariable UUID productId,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved successfully",
                service.findByProduct(productId, pageable)));
    }

    @GetMapping("/rating-summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a product's rating summary",
            description = "Average rating, total review count, and a 1-5 star distribution - computed "
                    + "server-side over every active review, not just the currently displayed page.")
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> ratingSummary(@PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.success("Rating summary retrieved successfully",
                service.ratingSummary(productId)));
    }
}
