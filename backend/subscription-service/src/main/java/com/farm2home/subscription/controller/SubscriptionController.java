package com.farm2home.subscription.controller;

import com.farm2home.subscription.config.UserPrincipal;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Manage milk delivery subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService service;

    @PostMapping
    @Operation(summary = "Create a new subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Subscription created"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "422", description = "Business rule violation")
    })
    public ResponseEntity<SubscriptionResponse> create(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? request.getCustomerId() : principal.userId();
        if (customerId == null) customerId = principal.userId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, customerId));
    }

    @GetMapping
    @Operation(summary = "List subscriptions",
               description = "Customers see only their own. FARM_MANAGER/SUPER_ADMIN see all. "
                           + "Admins may optionally filter by customerId query param.")
    public ResponseEntity<Page<SubscriptionResponse>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer (admin only)")
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(service.findAll(filterBy, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription found"),
        @ApiResponse(responseCode = "404", description = "Not found or access denied")
    })
    public ResponseEntity<SubscriptionResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(service.findById(id, customerId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update subscription (milk type, quantity, schedule, end date)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription updated"),
        @ApiResponse(responseCode = "422", description = "Subscription not modifiable in current status")
    })
    public ResponseEntity<SubscriptionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(service.update(id, request, customerId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Subscription cancelled"),
        @ApiResponse(responseCode = "422", description = "Already cancelled or expired")
    })
    public ResponseEntity<Map<String, String>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        service.cancel(id, customerId);
        return ResponseEntity.ok(Map.of("message", "Subscription cancelled successfully."));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause an active subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription paused"),
        @ApiResponse(responseCode = "422", description = "Subscription is not ACTIVE")
    })
    public ResponseEntity<SubscriptionResponse> pause(
            @PathVariable UUID id,
            @Valid @RequestBody PauseSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(service.pause(id, request, customerId));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription resumed"),
        @ApiResponse(responseCode = "422", description = "Subscription is not PAUSED")
    })
    public ResponseEntity<SubscriptionResponse> resume(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(service.resume(id, customerId));
    }
}
