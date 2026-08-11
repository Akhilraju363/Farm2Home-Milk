package com.farm2home.delivery.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.dto.request.SubmitLocationRequest;
import com.farm2home.delivery.dto.response.LocationResponse;
import com.farm2home.delivery.service.impl.DeliveryLocationServiceImpl;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Real-time delivery tracking. Location submission is only accepted while an assignment is
 *  OUT_FOR_DELIVERY (see DeliveryLocationServiceImpl); reads are ownership-scoped three ways
 *  (admin sees any, a DELIVERY_PARTNER sees their own assignment, a CUSTOMER sees their own
 *  order's assignment). No @PreAuthorize on any endpoint here - same convention as the rest of
 *  DeliveryAssignmentController, where ownership is resolved inside the service layer and a
 *  mismatch is a 404, not a 403, so a caller can never confirm another party's assignment exists. */
@RestController
@RequestMapping("/api/v1/delivery/assignments/{assignmentId}")
@Tag(name = "Delivery Tracking", description = "Real-time GPS location for an active delivery assignment")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryLocationController {

    private final DeliveryLocationServiceImpl locationService;

    @PostMapping("/location")
    @Operation(summary = "Submit the caller's current GPS location for this assignment (delivery partner)",
            description = "Only accepted while the assignment is OUT_FOR_DELIVERY. The caller must be the "
                    + "delivery partner this assignment belongs to (resolved from the bearer token) - a caller "
                    + "with no DeliveryPartner profile, or whose profile doesn't own this assignment, gets 404. "
                    + "recordedAt is stamped server-side, never taken from the request.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Location recorded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Invalid coordinates, or the assignment is not currently OUT_FOR_DELIVERY", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Assignment not found, belongs to a different delivery partner, or the caller has no delivery partner profile",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<LocationResponse>> submitLocation(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody SubmitLocationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Location recorded successfully",
                locationService.submitLocation(assignmentId, request, principal.userId())));
    }

    @GetMapping("/location")
    @Operation(summary = "Get this assignment's current location",
            description = "Ownership-scoped: admin sees any assignment, a DELIVERY_PARTNER sees their own, a "
                    + "CUSTOMER sees their own order's assignment. Returns null data (not an error) once the "
                    + "assignment is DELIVERED/FAILED (tracking has ended), or if no location has been "
                    + "submitted yet.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Current location, or null if tracking hasn't started/has ended"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Assignment not found or does not belong to this caller", content = @Content)
    })
    public ResponseEntity<ApiResponse<LocationResponse>> getCurrentLocation(
            @PathVariable UUID assignmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Location retrieved successfully",
                locationService.getCurrentLocation(assignmentId, principal).orElse(null)));
    }

    @GetMapping("/locations")
    @Operation(summary = "Get this assignment's location history",
            description = "Same ownership scoping as GET .../location, most recent first, paginated.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Location history retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Assignment not found or does not belong to this caller", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<LocationResponse>>> getHistory(
            @PathVariable UUID assignmentId, @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Location history retrieved successfully",
                locationService.getHistory(assignmentId, principal, pageable)));
    }
}
