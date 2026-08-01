package com.farm2home.order.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.order.config.UserPrincipal;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    @Operation(summary = "Create a manual one-time order",
            description = "Order must have at least one item. For a same-day order (orderDate = today), the "
                    + "requested quantity plus every other order already placed for today is checked against "
                    + "today's actual milk production total (order-service calls production-service) - "
                    + "future-dated orders skip this check entirely, since production for a future date can "
                    + "never exist yet.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error (no items, orderDate in the past)", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Same-day order would exceed today's milk production capacity")
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
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "orderDate") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Orders retrieved successfully", orderService.findAll(filterBy, pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search orders",
               description = "Keyword search across order number/notes, plus optional date range, status, and "
                           + "milk type filters. CUSTOMER sees only their own orders. Admins see all, with an "
                           + "optional customerId filter.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Matches order number or notes") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) MilkType milkType,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "orderDate") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully",
                orderService.search(filterBy, keyword, dateFrom, dateTo, status, milkType, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID",
            description = "Customers can only fetch their own orders; admins can fetch any.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
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
    @Operation(summary = "Get all orders for a subscription",
            description = "Every order generated by the daily subscription-order job for one subscription, "
                    + "most recent orderDate first. No ownership check against the caller - any authenticated "
                    + "caller who knows the subscriptionId can view its orders.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders retrieved (possibly empty if the subscription is new or has no matching orders)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getBySubscription(
            @Parameter(description = "The subscription whose generated orders to list")
            @PathVariable UUID subscriptionId,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "orderDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription orders retrieved successfully", orderService.findBySubscription(subscriptionId, pageable)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status",
               description = "Valid transitions: PENDING→ASSIGNED, ASSIGNED→OUT_FOR_DELIVERY, "
                           + "OUT_FOR_DELIVERY→DELIVERED. Any active status→CANCELLED. DELIVERED and "
                           + "CANCELLED are terminal - no further transition is ever allowed out of either.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or access denied", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid status transition (target status is not reachable from the current one)")
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
    @Operation(summary = "Cancel an order",
            description = "Transitions the order to CANCELLED. Not a hard delete - the order row and its "
                    + "history are preserved. Valid from any non-terminal status (PENDING, ASSIGNED, "
                    + "OUT_FOR_DELIVERY); DELIVERED and already-CANCELLED orders reject this.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order cancelled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or access denied", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Order is already DELIVERED or CANCELLED")
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
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Trigger daily subscription order generation (admin only)",
               description = "Generates subscription-based orders for a given date. "
                           + "Idempotent — skips if order already exists for subscription+date.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Generation result"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<GenerationResultResponse>> generateOrders(
            @Parameter(description = "Target date (defaults to today)", example = "2026-06-24")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.success(
                "Daily orders generated successfully", generationService.generateOrdersForDate(targetDate)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get order summary metrics for the dashboard",
            description = "Platform-wide today's-orders and pending-orders counts, for the admin/ops dashboard.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Summary retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER authority", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<OrderSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                "Order summary retrieved successfully", orderService.getSummary()));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Sales Report",
            description = "Filtered, paginated orders plus summary totals (order count, revenue). "
                    + "All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER authority", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<SalesReportRow, SalesReportSummary>>> getReport(
            @Parameter(description = "Order date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Order date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Filter to orders containing at least one item of this milk type")
            @RequestParam(required = false) MilkType milkType,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "orderDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Sales report retrieved successfully",
                orderService.getReport(dateFrom, dateTo, status, customerId, milkType, pageable)));
    }

    @GetMapping("/export")
    @Operation(summary = "Export orders", description = "Streams orders matching the same filters as "
            + "GET /search to a downloadable CSV, Excel, or PDF file. Runs on Spring MVC's async dispatch "
            + "thread (via StreamingResponseBody) and fetches rows in bounded pages, so large exports don't "
            + "block a request-handling thread or require holding the full result set in memory.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "File stream started"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "format is missing or not one of CSV/EXCEL/PDF", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<StreamingResponseBody> export(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @RequestParam ExportFormat format,
            @Parameter(description = "Matches order number or notes") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) MilkType milkType,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        String filename = "orders-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = out ->
                orderService.export(format, out, filterBy, keyword, dateFrom, dateTo, status, milkType, sortBy, ascending);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }
}
