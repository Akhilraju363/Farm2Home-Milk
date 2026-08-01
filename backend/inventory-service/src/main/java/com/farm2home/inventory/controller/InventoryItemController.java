package com.farm2home.inventory.controller;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.InventoryConsumptionPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.InventorySummaryResponse;
import com.farm2home.common.core.reports.InventoryReportRow;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.enums.TxnType;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.request.UpdateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.service.impl.InventoryItemServiceImpl;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory Items", description = "Farm inventory items (feed, medicine, equipment) - definitions, "
        + "reorder thresholds, and stock-level reporting. Stock quantity itself is only ever changed via the "
        + "stock transaction endpoints (see Stock Transactions), never directly through this controller.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class InventoryItemController {

    private final InventoryItemServiceImpl service;
    private final StockTransactionServiceImpl txnService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Create inventory item",
            description = "Registers a new inventory item definition (name, type, unit, reorder level, price, "
                    + "supplier). The item's stock quantity always starts at 0 - this endpoint does not accept an "
                    + "initial quantity; stock must be added afterward via POST /api/v1/inventory/{itemId}/transactions.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Inventory item created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing item name/type/unit, or a negative reorder level/unit price)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content)
    })
    public ResponseEntity<ApiResponse<InventoryItemResponse>> create(
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inventory item created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List inventory items",
            description = "Paginated inventory items, optionally filtered to a single item type. Soft-deleted "
                    + "items are always excluded.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory items retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<InventoryItemResponse>>> findAll(
            @Parameter(description = "Optional item-type filter") @RequestParam(required = false) ItemType type,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Inventory items retrieved successfully", service.findAll(type, pageable)));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search inventory items", description = "Keyword search across item name/supplier, plus "
            + "optional date range and item-type filters. All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory items retrieved (possibly empty if no item matches the filters)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<InventoryItemResponse>>> search(
            @Parameter(description = "Matches item name or supplier") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) ItemType itemType,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "itemName") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Inventory items retrieved successfully",
                service.search(keyword, dateFrom, dateTo, itemType, pageable)));
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export inventory items", description = "Streams inventory items matching the same "
            + "filters as GET /search to a downloadable CSV, Excel, or PDF file. Runs on Spring MVC's async "
            + "dispatch thread (via StreamingResponseBody) and fetches rows in bounded pages, so large exports "
            + "don't block a request-handling thread or require holding the full result set in memory.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream returned with a Content-Disposition attachment header",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "format is missing/invalid, or sortBy references a non-existent field", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam ExportFormat format,
            @Parameter(description = "Matches item name or supplier") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(defaultValue = "itemName") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {
        String filename = "inventory-items-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = out ->
                service.export(format, out, keyword, dateFrom, dateTo, itemType, sortBy, ascending);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get inventory item by ID",
            description = "Fetches a single inventory item, including its current quantity and whether it is "
                    + "currently at or below its reorder level (`belowReorderLevel`).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory item retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Inventory item retrieved successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "itemName": "Cattle Feed - Premium Mix",
                                    "itemType": "FEED",
                                    "quantity": 250.00,
                                    "unit": "KG",
                                    "reorderLevel": 50.00,
                                    "belowReorderLevel": false,
                                    "unitPrice": 32.50,
                                    "supplier": "Green Valley Suppliers",
                                    "createdAt": "2026-07-01T09:00:00",
                                    "createdBy": "9876543210"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No inventory item with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<InventoryItemResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory item retrieved successfully", service.findById(id)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List low-stock inventory items",
            description = "All non-deleted items whose current quantity is at or below their configured reorder "
                    + "level, unpaged. The same set feeds the daily 08:00 low-stock alert sweep "
                    + "(InventoryItemServiceImpl.alertLowStockItems).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Low-stock items retrieved (empty list if nothing is below its reorder level)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> lowStock() {
        return ResponseEntity.ok(ApiResponse.success("Low-stock items retrieved successfully", service.findLowStock()));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get low-stock inventory summary for the dashboard",
            description = "Dashboard widget data: total count of items at/below reorder level, plus the top "
                    + "`limit` of them (default 5) ordered as returned by the low-stock query.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory summary retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<InventorySummaryResponse>> getSummary(
            @Parameter(description = "Max number of low-stock items to include") @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Inventory summary retrieved successfully", service.getSummary(limit)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update inventory item",
            description = "Partial update of an inventory item's name/reorder level/unit price/supplier - any "
                    + "field left null in the request body is left unchanged (see "
                    + "InventoryMapper.updateItemFromRequest, which uses MapStruct's null-ignore strategy). "
                    + "Quantity cannot be changed here; it only moves via stock transactions.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory item updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. a negative reorder level/unit price)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No inventory item with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<InventoryItemResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Inventory item updated successfully", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Delete inventory item",
            description = "Soft-deletes the item (sets a deleted flag); it stops appearing in listings, search, "
                    + "low-stock, and reports, but its historical stock transactions are retained.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory item deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No inventory item with this ID (or it has already been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Inventory item deleted successfully", null));
    }

    @GetMapping("/transactions/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Inventory Report",
            description = "Filtered, paginated stock transaction history plus summary totals "
                    + "(transaction count, IN/OUT quantity totals). All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<InventoryReportRow, InventoryReportSummary>>> getReport(
            @Parameter(description = "Transaction date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Transaction date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) TxnType txnType,
            @RequestParam(required = false) UUID itemId,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "transactedAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Inventory report retrieved successfully",
                txnService.getReport(dateFrom, dateTo, txnType, itemId, pageable)));
    }

    @GetMapping("/analytics/consumption-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Inventory Consumption Trend", description = "Total quantity consumed (OUT "
            + "transactions) bucketed by the requested granularity (day/week/month/year). dateFrom/dateTo are "
            + "optional and bound transactedAt; the GROUP BY/SUM aggregation runs in the database.")
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
    public ResponseEntity<ApiResponse<TrendSeries<InventoryConsumptionPoint>>> getConsumptionTrend(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Inventory consumption trend retrieved successfully",
                txnService.getConsumptionTrend(granularity, dateFrom, dateTo)));
    }
}
