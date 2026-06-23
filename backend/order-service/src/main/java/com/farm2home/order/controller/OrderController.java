package com.farm2home.order.controller;

import com.farm2home.order.config.UserPrincipal;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.GenerationResultResponse;
import com.farm2home.order.dto.response.OrderResponse;
import com.farm2home.order.service.DailyOrderGenerationService;
import com.farm2home.order.service.OrderService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
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
        @ApiResponse(responseCode = "201", description = "Order created"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin()
                ? (request.getCustomerId() != null ? request.getCustomerId() : principal.userId())
                : principal.userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createManualOrder(request, customerId));
    }

    @GetMapping
    @Operation(summary = "List orders",
               description = "CUSTOMER sees only their own orders. Admin roles see all. "
                           + "Optional customerId filter for admins.")
    public ResponseEntity<Page<OrderResponse>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(orderService.findAll(filterBy, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "404", description = "Not found or access denied")
    })
    public ResponseEntity<OrderResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(orderService.findById(id, customerId));
    }

    @GetMapping("/subscription/{subscriptionId}")
    @Operation(summary = "Get all orders for a subscription")
    public ResponseEntity<Page<OrderResponse>> getBySubscription(
            @PathVariable UUID subscriptionId,
            @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
        return ResponseEntity.ok(orderService.findBySubscription(subscriptionId, pageable));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status",
               description = "Valid transitions: PENDING→ASSIGNED, ASSIGNED→OUT_FOR_DELIVERY, "
                           + "OUT_FOR_DELIVERY→DELIVERED. Any active status→CANCELLED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "422", description = "Invalid status transition")
    })
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(
                orderService.updateStatus(id, request, customerId, principal.isAdmin()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order cancelled"),
        @ApiResponse(responseCode = "422", description = "Already delivered or cancelled")
    })
    public ResponseEntity<Map<String, String>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        orderService.cancel(id, customerId, principal.isAdmin());
        return ResponseEntity.ok(Map.of("message", "Order cancelled successfully."));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Trigger daily subscription order generation (admin only)",
               description = "Generates subscription-based orders for a given date. "
                           + "Idempotent — skips if order already exists for subscription+date.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Generation result"),
        @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ResponseEntity<GenerationResultResponse> generateOrders(
            @Parameter(description = "Target date (defaults to today)", example = "2026-06-24")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(generationService.generateOrdersForDate(targetDate));
    }
}
