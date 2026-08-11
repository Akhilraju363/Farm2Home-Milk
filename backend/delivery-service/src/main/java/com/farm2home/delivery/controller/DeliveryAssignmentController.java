package com.farm2home.delivery.controller;

import com.farm2home.common.core.analytics.DeliveryPerformancePoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.DeliverySummaryResponse;
import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.dto.request.DelayAssignmentRequest;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.service.DeliveryAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tracks the lifecycle of a single order's delivery: which partner/route it's assigned to, and
 * its status as it moves ASSIGNED -&gt; OUT_FOR_DELIVERY -&gt; DELIVERED (or -&gt; FAILED from
 * either of the first two states) - see {@link AssignmentStatus#canTransitionTo}. Endpoints
 * without an explicit {@code @PreAuthorize} are open to any authenticated caller; ownership
 * filtering for those (admin sees all, a delivery partner sees only their own) is applied inside
 * DeliveryAssignmentServiceImpl based on the caller's principal, not via method security.
 */
@RestController
@RequestMapping("/api/v1/delivery/assignments")
@Tag(name = "Delivery Assignments", description = "Delivery assignment tracking: creating assignments, "
        + "querying them, and driving their ASSIGNED -> OUT_FOR_DELIVERY -> DELIVERED/FAILED status lifecycle.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Manually assign an order to a delivery partner (admin)",
            description = "Creates a new delivery assignment linking an order to a delivery partner and route. "
                    + "An order may only have one assignment - a second manualAssign call for the same order is "
                    + "rejected. Publishes a DELIVERY_ASSIGNED event on success.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Assignment created",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Assignment created successfully",
                                  "data": {
                                    "id": "c1a2b3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                    "orderId": "b3f1a2e4-5c6d-4e7f-8a9b-0c1d2e3f4a5b",
                                    "deliveryPartnerId": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "deliveryPartnerName": "Ramesh Kumar",
                                    "deliveryPartnerMobile": "9876543210",
                                    "routeId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeCode": "NZ-01",
                                    "status": "ASSIGNED",
                                    "assignedAt": "2026-07-31T09:15:00",
                                    "createdAt": "2026-07-31T09:15:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failure (missing orderId/deliveryPartnerId/routeId), or the order "
                        + "already has a delivery assignment", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "deliveryPartnerId or routeId does not reference an existing, non-deleted record",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<AssignmentResponse>> manualAssign(
            @Valid @RequestBody ManualAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assignment created successfully", assignmentService.manualAssign(request)));
    }

    @GetMapping
    @Operation(summary = "List assignments (admin sees all, partner sees own)",
            description = "Paginated assignment list, most recently assigned first by default. Open to any "
                    + "authenticated caller (no @PreAuthorize) - admin (FARM_MANAGER/SUPER_ADMIN) callers see "
                    + "every assignment; non-admin callers are first resolved from their principal userId to "
                    + "their own DeliveryPartner profile (DeliveryPartnerRepository.findByUserIdAndDeletedFalse, "
                    + "same resolution findById/updateStatus/markDelayed use), then filtered to assignments "
                    + "belonging to that partner.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Assignments retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Caller is not an admin and has no DeliveryPartner profile", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<AssignmentResponse>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "assignedAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Assignments retrieved successfully", assignmentService.findAll(
                principal.userId(), principal.isAdmin(), pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search assignments",
               description = "Keyword search across delivery partner name / route name / area / city, plus "
                           + "optional date range and status filters (all combine with AND). Admin sees all; "
                           + "non-admin callers are filtered the same way as GET / above (resolved to the "
                           + "caller's own DeliveryPartner profile first).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Assignments retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Caller is not an admin and has no DeliveryPartner profile", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<AssignmentResponse>>> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Matches delivery partner name or route name/area/city")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) AssignmentStatus status,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "assignedAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Assignments retrieved successfully", assignmentService.search(
                principal.userId(), principal.isAdmin(), keyword, dateFrom, dateTo, status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get assignment by ID",
            description = "Admin callers may fetch any assignment. Non-admin callers are first resolved from "
                    + "their principal userId to their own DeliveryPartner profile "
                    + "(DeliveryPartnerRepository.findByUserIdAndDeletedFalse), then the assignment must belong "
                    + "to that partner - otherwise a 404 is returned (not 403), to avoid confirming the "
                    + "assignment's existence to a caller who doesn't own it.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Assignment retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Assignment retrieved successfully",
                                  "data": {
                                    "id": "c1a2b3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                    "orderId": "b3f1a2e4-5c6d-4e7f-8a9b-0c1d2e3f4a5b",
                                    "deliveryPartnerId": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "deliveryPartnerName": "Ramesh Kumar",
                                    "deliveryPartnerMobile": "9876543210",
                                    "routeId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeCode": "NZ-01",
                                    "status": "OUT_FOR_DELIVERY",
                                    "assignedAt": "2026-07-31T09:15:00",
                                    "createdAt": "2026-07-31T09:15:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Assignment not found, or (non-admin caller) exists but belongs to a different "
                        + "delivery partner, or the caller has no delivery partner profile at all", content = @Content)
    })
    public ResponseEntity<ApiResponse<AssignmentResponse>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Assignment retrieved successfully", assignmentService.findById(id, principal.userId(), principal.isAdmin())));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get all assignments for an order",
            description = "All assignments ever created for the given order (normally at most one, since "
                    + "manualAssign rejects a second assignment for the same order). Admin sees any order's "
                    + "assignments; a DELIVERY_PARTNER sees only assignments that are theirs; a CUSTOMER sees "
                    + "only assignments for their own order (matched via DeliveryAssignment.customerId, now "
                    + "reliably populated for both auto- and manually-created assignments - see "
                    + "manualAssign()). Any other caller, or a mismatched order, gets an empty list rather than "
                    + "an error, so this never confirms whether an unrelated order/assignment exists.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Assignments retrieved (empty if never assigned, or the caller doesn't own any of them)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> findByOrder(
            @PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Order assignments retrieved successfully",
                assignmentService.findByOrderId(orderId, principal.userId(), principal.isAdmin())));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update delivery assignment status (partner or admin)",
            description = "Moves the assignment to a new status. Only the transitions allowed by "
                    + "AssignmentStatus.canTransitionTo() are legal: ASSIGNED -> OUT_FOR_DELIVERY or FAILED; "
                    + "OUT_FOR_DELIVERY -> DELIVERED or FAILED; DELIVERED and FAILED are terminal (no further "
                    + "transitions from either). Dispatching to OUT_FOR_DELIVERY additionally requires the "
                    + "order to have at least a PENDING or SUCCESS payment in payment-service "
                    + "(PaymentServiceClient.hasPayableProgress) - an order with no payment initiated cannot "
                    + "be dispatched. Transitioning to FAILED requires a non-blank failureReason (persisted); "
                    + "transitioning to DELIVERED requires a non-blank deliveryProof (persisted) and stamps "
                    + "deliveredAt. Ownership is resolved the same way as GET /{id}: non-admin callers may only "
                    + "update assignments belonging to their own DeliveryPartner profile.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Status updated",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Assignment status updated successfully",
                                  "data": {
                                    "id": "c1a2b3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                    "orderId": "b3f1a2e4-5c6d-4e7f-8a9b-0c1d2e3f4a5b",
                                    "deliveryPartnerId": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "deliveryPartnerName": "Ramesh Kumar",
                                    "deliveryPartnerMobile": "9876543210",
                                    "routeId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeCode": "NZ-01",
                                    "status": "DELIVERED",
                                    "assignedAt": "2026-07-31T09:15:00",
                                    "deliveredAt": "2026-07-31T14:20:00",
                                    "deliveryProof": "signed-by-customer",
                                    "createdAt": "2026-07-31T09:15:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failure (status/failureReason/deliveryProof missing - note "
                        + "failureReason and deliveryProof are currently required on every call regardless of "
                        + "target status, see UpdateAssignmentStatusRequest), an illegal status transition, or "
                        + "(dispatching to OUT_FOR_DELIVERY) no payment has been initiated for the order yet",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Assignment not found, belongs to a different delivery partner (non-admin "
                        + "caller), or the caller has no delivery partner profile", content = @Content)
    })
    public ResponseEntity<ApiResponse<AssignmentResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssignmentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Assignment status updated successfully", assignmentService.updateStatus(
                id, request, principal.userId(), principal.isAdmin())));
    }

    @PostMapping("/{id}/delay")
    @Operation(summary = "Notify the customer that this delivery is running late (partner or admin)",
            description = "Sends a DELIVERY_DELAYED notification event; does not change the assignment's "
                    + "persisted status or any other field (see DelayAssignmentRequest). Rejected if the "
                    + "assignment is already in a terminal status (DELIVERED or FAILED). Ownership is resolved "
                    + "the same way as GET /{id}.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Delay notice sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "reason missing/blank, or the assignment is already DELIVERED/FAILED", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Assignment not found, belongs to a different delivery partner (non-admin "
                        + "caller), or the caller has no delivery partner profile", content = @Content)
    })
    public ResponseEntity<ApiResponse<AssignmentResponse>> markDelayed(
            @PathVariable UUID id,
            @Valid @RequestBody DelayAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Delay notice sent successfully", assignmentService.markDelayed(
                id, request, principal.userId(), principal.isAdmin())));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get delivery summary metrics for the dashboard",
            description = "Currently reports a single metric: the count of assignments that transitioned to "
                    + "DELIVERED today (server-local date, midnight to midnight). Restricted to "
                    + "SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER - note this uses hasAnyAuthority(...) against "
                    + "raw role strings, not hasAnyRole(...), unlike most of this controller.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Summary retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Delivery summary retrieved successfully",
                                  "data": {
                                    "completedDeliveriesToday": 42
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<DeliverySummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Delivery summary retrieved successfully", assignmentService.getSummary()));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Delivery Report",
            description = "Filtered, paginated delivery assignments plus summary totals (total, completed, "
                    + "failed counts - completed/failed counts reflect only the dateFrom/dateTo/orderIds "
                    + "filters, not the status filter, since they exist to answer \"of the date-filtered set, "
                    + "how many completed/failed\"). All filters are optional and combine with AND. Restricted "
                    + "to SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Report retrieved (possibly empty content if no assignments match the filters)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<DeliveryReportRow, DeliveryReportSummary>>> getReport(
            @Parameter(description = "Assignment date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Assignment date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) List<UUID> orderIds,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "assignedAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Delivery report retrieved successfully",
                assignmentService.getReport(dateFrom, dateTo, status, orderIds, pageable)));
    }

    @GetMapping("/analytics/performance-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Delivery Performance Trend", description = "Total/completed/failed delivery counts "
            + "bucketed by the requested granularity (day/week/month/year). dateFrom/dateTo are optional and "
            + "bound assignedAt; the GROUP BY/COUNT aggregation runs in the database. Restricted to "
            + "SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Trend retrieved (possibly empty points if no assignments fall in range)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "granularity missing or not one of the supported values", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<TrendSeries<DeliveryPerformancePoint>>> getPerformanceTrend(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Delivery performance trend retrieved successfully",
                assignmentService.getPerformanceTrend(granularity, dateFrom, dateTo)));
    }
}
