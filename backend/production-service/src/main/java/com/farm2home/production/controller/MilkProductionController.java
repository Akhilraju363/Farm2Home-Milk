package com.farm2home.production.controller;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.ProductionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.ProductionSummaryResponse;
import com.farm2home.common.core.reports.ProductionReportRow;
import com.farm2home.common.core.reports.ProductionReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.production.config.UserPrincipal;
import com.farm2home.production.domain.enums.QualityGrade;
import com.farm2home.production.dto.request.CreateMilkProductionRequest;
import com.farm2home.production.dto.request.UpdateMilkProductionRequest;
import com.farm2home.production.dto.response.DailySummaryResponse;
import com.farm2home.production.dto.response.MilkProductionResponse;
import com.farm2home.production.service.impl.MilkProductionServiceImpl;
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

@RestController
@RequestMapping("/api/v1/productions")
@Tag(name = "Milk Production", description = "Per-cow, per-session (morning/evening) milk collection records: "
        + "quantity, quality (fat/SNF percentage, grade), daily summaries, and reporting/analytics.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MilkProductionController {

    private final MilkProductionServiceImpl service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create milk production record",
            description = "Records a milk collection for one cow on one collection date/session (MORNING or "
                    + "EVENING). At most one record may exist per (cowId, collectionDate, session) - a duplicate "
                    + "is rejected (see MilkProductionServiceImpl.create). collectionDate cannot be in the future. "
                    + "This service does not check the cow's status (e.g. active/sold) before recording production.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Milk production record created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing cowId/session/quantity, quantity not greater than "
                        + "zero, or collectionDate in the future), OR a record already exists for this cow/date/"
                        + "session (both cases return 400, since both MethodArgumentNotValidException and "
                        + "ProductionException map to HTTP 400)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<MilkProductionResponse>> create(
            @Valid @RequestBody CreateMilkProductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Milk production record created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List milk production records",
            description = "Paginated milk production records across all cows. Soft-deleted records are always excluded.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Milk production records retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<MilkProductionResponse>>> findAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Milk production records retrieved successfully", service.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get milk production record by ID",
            description = "Fetches a single milk production record, including quality fields (fat/SNF percentage, grade).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Milk production record retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Milk production record retrieved successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "cowId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "collectionDate": "2026-07-31",
                                    "session": "MORNING",
                                    "quantityLiters": 12.50,
                                    "fatPercentage": 4.20,
                                    "snfPercentage": 8.60,
                                    "qualityGrade": "A",
                                    "collectedBy": "Ramesh Kumar",
                                    "notes": "Normal collection",
                                    "createdAt": "2026-07-31T06:15:00",
                                    "createdBy": "9876543210"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No milk production record with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<MilkProductionResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Milk production record retrieved successfully", service.findById(id)));
    }

    @GetMapping("/cow/{cowId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List milk production records for a cow",
            description = "Paginated milk production records for a single cow, across all collection dates/sessions.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Milk production records retrieved (possibly empty if this cow has no records)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<MilkProductionResponse>>> findByCow(
            @PathVariable UUID cowId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Milk production records for cow retrieved successfully", service.findByCow(cowId, pageable)));
    }

    @GetMapping("/cow/{cowId}/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get daily production summary for a cow",
            description = "Total liters and record count per collection date for one cow, over the inclusive "
                    + "[from, to] date range. Aggregation runs in the database (repository.findDailySummaryByCow).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Daily summary retrieved (possibly empty if this cow has no records in range)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "from or to is missing/malformed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<DailySummaryResponse>>> cowSummary(
            @PathVariable UUID cowId,
            @Parameter(description = "Range start (inclusive)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Range end (inclusive)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Cow daily summary retrieved successfully", service.getDailySummaryByCow(cowId, from, to)));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get daily production summary across all cows",
            description = "Total liters and record count per collection date across all cows, over the inclusive "
                    + "[from, to] date range. Aggregation runs in the database (repository.findDailySummary). "
                    + "Distinct from GET /summary/today below, which is a single-day dashboard total for today only.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Daily summary retrieved (possibly empty if no records exist in range)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "from or to is missing/malformed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<DailySummaryResponse>>> summary(
            @Parameter(description = "Range start (inclusive)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Range end (inclusive)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Daily summary retrieved successfully", service.getDailySummary(from, to)));
    }

    // Distinct path from "/summary" above (which requires from/to range params) - this one
    // is the dashboard-service aggregation endpoint for today's total production only.
    @GetMapping("/summary/today")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get today's total milk production summary for the dashboard",
            description = "Single dashboard figure: total liters collected today (LocalDate.now()) across all "
                    + "cows/sessions. Returns 0 (not an error) if nothing has been recorded yet today.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Production summary retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductionSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Production summary retrieved successfully", service.getSummary()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update milk production record",
            description = "Partial update of a production record's quantity/fat%/SNF%/quality grade/collectedBy/"
                    + "notes - any field left null in the request body is left unchanged (see "
                    + "ProductionMapper.updateEntityFromRequest, which uses MapStruct's null-ignore strategy). "
                    + "cowId, collectionDate, and session cannot be changed via this endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Milk production record updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. quantity not greater than zero, or fat/SNF percentage out of range)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No milk production record with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<MilkProductionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMilkProductionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Milk production record updated successfully", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Delete milk production record",
            description = "Soft-deletes the record (sets a deleted flag); it stops appearing in listings, "
                    + "summaries, and reports.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Milk production record deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No milk production record with this ID (or it has already been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Milk production record deleted successfully", null));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Production Report",
            description = "Filtered, paginated milk production records plus summary totals (record count, "
                    + "total liters). All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<ProductionReportRow, ProductionReportSummary>>> getReport(
            @Parameter(description = "Collection date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Collection date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) QualityGrade qualityGrade,
            @Parameter(description = "Comma-separated list of cow UUIDs to filter by")
            @RequestParam(required = false) List<UUID> cowIds,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "collectionDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Production report retrieved successfully",
                service.getReport(dateFrom, dateTo, qualityGrade, cowIds, pageable)));
    }

    @GetMapping("/analytics/production-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Milk Production Trend", description = "Total liters collected bucketed by the "
            + "requested granularity (day/week/month/year). dateFrom/dateTo are optional and bound "
            + "collectionDate; the GROUP BY/SUM aggregation runs in the database.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Trend series retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "granularity is missing or not one of DAILY/WEEKLY/MONTHLY/YEARLY", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<TrendSeries<ProductionTrendPoint>>> getProductionTrend(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Production trend retrieved successfully",
                service.getProductionTrend(granularity, dateFrom, dateTo)));
    }
}
