package com.farm2home.inventory.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.config.UserPrincipal;
import com.farm2home.inventory.dto.request.CreateReviewRequest;
import com.farm2home.inventory.dto.request.UpdateReviewRequest;
import com.farm2home.inventory.dto.response.ReviewResponse;
import com.farm2home.inventory.service.impl.ReviewServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Reviews", description = "Customer self-service for rating/reviewing delivered order products. "
        + "See ProductReviewController for a product's public review list and rating summary.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewServiceImpl service;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_CUSTOMER + "')")
    @Operation(summary = "Submit a review",
            description = "Rates/reviews a product from one of the caller's own delivered orders. customerId is "
                    + "always taken from the bearer token. Rejected (400) if the order isn't DELIVERED yet, "
                    + "(404) if the order doesn't exist or belongs to someone else, or (409) if this customer "
                    + "already reviewed this product for this order.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Review created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed, or the order is not yet DELIVERED", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not CUSTOMER", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Product or order not found (or the order belongs to someone else)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                description = "This customer already reviewed this product for this order", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody CreateReviewRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully", service.create(principal.userId(), request)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_CUSTOMER + "')")
    @Operation(summary = "List my reviews", description = "Paginated list of the caller's own reviews.")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> findMy(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved successfully",
                service.findMyReviews(principal.userId(), pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_CUSTOMER + "')")
    @Operation(summary = "Edit my review", description = "Updates rating/text on one of the caller's own reviews.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No such review, or it belongs to someone else", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Review updated successfully",
                service.update(principal.userId(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_CUSTOMER + "','"
            + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Delete a review",
            description = "A CUSTOMER may only delete their own review. SUPER_ADMIN/FARM_MANAGER may delete any "
                    + "review (moderation).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No such review, or (for a non-admin) it belongs to someone else", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully", null));
    }
}
