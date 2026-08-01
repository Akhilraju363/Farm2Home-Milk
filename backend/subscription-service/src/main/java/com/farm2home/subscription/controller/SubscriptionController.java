package com.farm2home.subscription.controller;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.SubscriptionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.SubscriptionSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.subscription.config.UserPrincipal;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.PauseSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import com.farm2home.subscription.service.SubscriptionService;
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
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Manage milk delivery subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService service;

    @PostMapping
    @Operation(summary = "Create a new subscription",
            description = "Starts as ACTIVE immediately. Validation: startDate cannot be in the past; endDate "
                    + "(if given) must be after startDate; a WEEKLY scheduleType requires at least one "
                    + "deliveryDay. Business rule: a customer cannot have two ACTIVE subscriptions for the "
                    + "same milkType at once — cancel or pause the existing one first.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Subscription created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Duplicate ACTIVE subscription for this customer+milkType")
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? request.getCustomerId() : principal.userId();
        if (customerId == null) customerId = principal.userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Subscription created successfully", service.create(request, customerId)));
    }

    @GetMapping
    @Operation(summary = "List subscriptions",
               description = "Customers see only their own. FARM_MANAGER/SUPER_ADMIN see all. "
                           + "Admins may optionally filter by customerId query param.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscriptions retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<Page<SubscriptionResponse>>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer (admin only)")
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscriptions retrieved successfully", service.findAll(filterBy, pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search subscriptions",
               description = "Keyword search across milk type/schedule type, plus optional date range, status, and "
                           + "milk type filters. Customers see only their own subscriptions. Admins see all, with an "
                           + "optional customerId filter.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscriptions retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<Page<SubscriptionResponse>>> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Matches milk type or schedule type") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) MilkType milkType,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "startDate") Pageable pageable) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        return ResponseEntity.ok(ApiResponse.success("Subscriptions retrieved successfully",
                service.search(filterBy, keyword, dateFrom, dateTo, status, milkType, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription by ID",
            description = "Customers can only fetch their own subscriptions; admins can fetch any.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found or access denied")
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription retrieved successfully", service.findById(id, customerId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update subscription (milk type, quantity, schedule, end date)",
            description = "Only ACTIVE or PAUSED subscriptions can be modified (CANCELLED/EXPIRED are "
                    + "terminal). If endDate is supplied it must be after the subscription's startDate; "
                    + "switching scheduleType to WEEKLY requires at least one deliveryDay.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Subscription is CANCELLED or EXPIRED (not modifiable)")
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription updated successfully", service.update(id, request, customerId)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a subscription",
            description = "Transitions the subscription to CANCELLED (a terminal status) and publishes a "
                    + "SUBSCRIPTION_CANCELLED event (see docs/PUSH_NOTIFICATION_INTEGRATION.md for who "
                    + "consumes it). Valid from ACTIVE or PAUSED only.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription cancelled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Already CANCELLED or EXPIRED")
    })
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        service.cancel(id, customerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription cancelled successfully.", null));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause an active subscription",
            description = "Valid from ACTIVE only. pauseEnd must be a future date; the subscription is "
                    + "auto-resumed by a nightly scheduled job once pauseEnd arrives (see "
                    + "SubscriptionServiceImpl.autoResumePausedSubscriptions).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription paused"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "pauseEnd is missing or not in the future", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Subscription is not ACTIVE")
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> pause(
            @PathVariable UUID id,
            @Valid @RequestBody PauseSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription paused successfully", service.pause(id, request, customerId)));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused subscription",
            description = "Valid from PAUSED only; clears pauseStart/pauseEnd and transitions back to ACTIVE.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription resumed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Subscription is not PAUSED")
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> resume(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal.isAdmin() ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription resumed successfully", service.resume(id, customerId)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get subscription summary metrics for the dashboard",
            description = "Platform-wide active-subscription count, for the admin/ops dashboard - not scoped to any one customer.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Summary retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER authority", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<SubscriptionSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription summary retrieved successfully", service.getSummary()));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Subscription Report",
            description = "Filtered, paginated subscriptions plus summary totals (count, active count, total quantity). "
                    + "All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER authority", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<SubscriptionReportRow, SubscriptionReportSummary>>> getReport(
            @Parameter(description = "Start date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Start date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) MilkType milkType,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "startDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Subscription report retrieved successfully",
                service.getReport(dateFrom, dateTo, status, customerId, milkType, pageable)));
    }

    @GetMapping("/analytics/subscription-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Subscription Trend", description = "Count of new subscriptions bucketed by the "
            + "requested granularity (day/week/month/year). dateFrom/dateTo are optional and bound startDate; "
            + "the GROUP BY/COUNT aggregation runs in the database.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trend retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "granularity is missing or not one of DAILY/WEEKLY/MONTHLY/YEARLY", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER authority", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ApiResponse<TrendSeries<SubscriptionTrendPoint>>> getSubscriptionTrend(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Subscription trend retrieved successfully",
                service.getSubscriptionTrend(granularity, dateFrom, dateTo)));
    }

    @GetMapping("/export")
    @Operation(summary = "Export subscriptions", description = "Streams subscriptions matching the same filters as "
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
            @RequestParam ExportFormat format,
            @Parameter(description = "Filter by customer UUID (admin only)")
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Matches milk type or schedule type") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) MilkType milkType,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {
        UUID filterBy = principal.isAdmin() ? customerId : principal.userId();
        String filename = "subscriptions-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = out ->
                service.export(format, out, filterBy, keyword, dateFrom, dateTo, status, milkType, sortBy, ascending);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }
}
