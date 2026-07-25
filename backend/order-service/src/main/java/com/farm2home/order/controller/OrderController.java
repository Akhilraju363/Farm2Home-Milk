package com.farm2home.order.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.order.config.UserPrincipal;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.dto.response.OrderResponse;
import com.farm2home.order.service.DailyOrderGenerationService;
import com.farm2home.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Manage milk delivery orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final DailyOrderGenerationService generationService;

    @PostMapping
    @Operation(summary = "Create a manual one-time order")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin()
                ? (request.getCustomerId() != null ? request.getCustomerId() : principal.userId())
                : principal.userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Order created successfully", orderService.createManualOrder(request, customerId)));
    }

    @GetMapping
    @Operation(summary = "List orders",
               description = "CUSTOMER sees only their own orders. Admin roles see all. "
                           + "Optional customerId filter for admins.")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Orders retrieved successfully", orderService.findAll(filterBy, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found or access denied")
    })
    public ResponseEntity<ApiResponse<OrderResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Order retrieved successfully", orderService.findById(id, customerId)));
    }

    @GetMapping("/subscription/{subscriptionId}")
    @Operation(summary = "Get all orders for a subscription")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getBySubscription(
            @PathVariable UUID subscriptionId,
            @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription orders retrieved successfully", orderService.findBySubscription(subscriptionId, pageable)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status",
               description = "Valid transitions: PENDING→ASSIGNED, ASSIGNED→OUT_FOR_DELIVERY, "
                           + "OUT_FOR_DELIVERY→DELIVERED. Any active status→CANCELLED.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid status transition")
    })
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Order status updated successfully",
                orderService.updateStatus(id, request, customerId, principal.isAdmin(), principal.userId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order cancelled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Already delivered or cancelled")
    })
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        orderService.cancel(id, customerId, principal.isAdmin(), principal.userId());
        return ResponseEntity.ok(ApiResponse.success(
                "Order cancelled successfully.", null));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Trigger daily subscription order generation (admin only)",
               description = "Generates subscription-based orders for a given date. "
                           + "Idempotent — skips if order already exists for subscription+date.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Generation result"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<ApiResponse<GenerationResultResponse>> generateOrders(
            @Parameter(description = "Target date (defaults to today)", example = "2026-06-24")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily orders generated successfully", generationService.generateOrdersForDate(targetDate)));
    }
}
