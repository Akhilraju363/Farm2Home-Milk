package com.farm2home.reports.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.DeliveryReportRow;
import com.farm2home.common.core.reports.DeliveryReportSummary;
import com.farm2home.common.core.reports.InventoryReportRow;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ProductionReportRow;
import com.farm2home.common.core.reports.ProductionReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.core.reports.SubscriptionReportRow;
import com.farm2home.common.core.reports.SubscriptionReportSummary;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Every report/export pair here proxies the owning service's own {@code /reports} (or
 * {@code /export}) endpoint rather than querying a local database - reports-service has no
 * database of its own. Because a proxy call is made over HTTP, {@code status}/{@code milkType}/
 * {@code txnType}/{@code qualityGrade} filters are necessarily plain {@code String} query params
 * here (changing them to a real enum type would require reports-service to depend on every
 * owning service's domain enum, which it deliberately does not) - but each one is documented
 * with the exact enum constant names the owning service actually accepts, via
 * {@code @Parameter(schema = @Schema(allowableValues = ...))}, so Swagger UI still renders a
 * dropdown of valid values even though the wire type stays String.
 *
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Filtered, paginated reports across every business area, "
        + "each proxied from its owning service so the frontend has one consistent surface")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '"
        + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales")
    @Operation(summary = "Sales Report", description = "Filters: dateRange (orderDate), status, customer, "
            + "product (milkType). Proxies order-service's own Sales Report endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Sales report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<SalesReportRow, SalesReportSummary>>> getSalesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "order-service OrderStatus enum name",
                    schema = @Schema(allowableValues = {"PENDING", "ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "order-service MilkType enum name",
                    schema = @Schema(allowableValues = {"FULL_CREAM", "TONED", "DOUBLE_TONED", "SKIMMED"}))
            @RequestParam(required = false) String milkType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Sales report retrieved successfully",
                reportService.getSalesReport(dateFrom, dateTo, status, customerId, milkType, page, size)));
    }

    @GetMapping("/sales/export")
    @Operation(summary = "Export Sales Report", description = "Streams the Sales Report matching the same "
            + "filters as GET /sales to a downloadable CSV, Excel, or PDF file. Runs on Spring MVC's async "
            + "dispatch thread and fetches rows in bounded pages from order-service, so large exports don't "
            + "block a request-handling thread or require holding the full result set in memory.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportSalesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "order-service OrderStatus enum name",
                    schema = @Schema(allowableValues = {"PENDING", "ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "order-service MilkType enum name",
                    schema = @Schema(allowableValues = {"FULL_CREAM", "TONED", "DOUBLE_TONED", "SKIMMED"}))
            @RequestParam(required = false) String milkType,
            @RequestParam ExportFormat format) {
        return export("sales-report", format, out ->
                reportService.exportSalesReport(format, out, dateFrom, dateTo, status, customerId, milkType));
    }

    @GetMapping("/customers")
    @Operation(summary = "Customer Report", description = "Filters: dateRange (createdAt), status. Proxies "
            + "customer-service's own Customer Report endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<CustomerReportRow, CustomerReportSummary>>> getCustomerReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "customer-service CustomerStatus enum name",
                    schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"}))
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Customer report retrieved successfully",
                reportService.getCustomerReport(dateFrom, dateTo, status, page, size)));
    }

    @GetMapping("/customers/export")
    @Operation(summary = "Export Customer Report", description = "Streams the Customer Report matching the same "
            + "filters as GET /customers to a downloadable CSV, Excel, or PDF file.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportCustomerReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "customer-service CustomerStatus enum name",
                    schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"}))
            @RequestParam(required = false) String status,
            @RequestParam ExportFormat format) {
        return export("customer-report", format, out ->
                reportService.exportCustomerReport(format, out, dateFrom, dateTo, status));
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Subscription Report", description = "Filters: dateRange (startDate), status, customer, "
            + "product (milkType). Proxies subscription-service's own Subscription Report endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Subscription report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<SubscriptionReportRow, SubscriptionReportSummary>>> getSubscriptionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "subscription-service SubscriptionStatus enum name",
                    schema = @Schema(allowableValues = {"ACTIVE", "PAUSED", "CANCELLED", "EXPIRED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "subscription-service MilkType enum name",
                    schema = @Schema(allowableValues = {"FULL_CREAM", "TONED", "DOUBLE_TONED", "SKIMMED"}))
            @RequestParam(required = false) String milkType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Subscription report retrieved successfully",
                reportService.getSubscriptionReport(dateFrom, dateTo, status, customerId, milkType, page, size)));
    }

    @GetMapping("/subscriptions/export")
    @Operation(summary = "Export Subscription Report", description = "Streams the Subscription Report matching "
            + "the same filters as GET /subscriptions to a downloadable CSV, Excel, or PDF file.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportSubscriptionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "subscription-service SubscriptionStatus enum name",
                    schema = @Schema(allowableValues = {"ACTIVE", "PAUSED", "CANCELLED", "EXPIRED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "subscription-service MilkType enum name",
                    schema = @Schema(allowableValues = {"FULL_CREAM", "TONED", "DOUBLE_TONED", "SKIMMED"}))
            @RequestParam(required = false) String milkType,
            @RequestParam ExportFormat format) {
        return export("subscription-report", format, out ->
                reportService.exportSubscriptionReport(format, out, dateFrom, dateTo, status, customerId, milkType));
    }

    @GetMapping("/inventory")
    @Operation(summary = "Inventory Report", description = "Stock transaction history. Filters: dateRange "
            + "(transactedAt), status (txnType), product (itemId). Proxies inventory-service's own "
            + "transaction report endpoint (GET /api/v1/inventory/transactions/reports).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Inventory report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<InventoryReportRow, InventoryReportSummary>>> getInventoryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "inventory-service TxnType enum name",
                    schema = @Schema(allowableValues = {"IN", "OUT"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Inventory report retrieved successfully",
                reportService.getInventoryReport(dateFrom, dateTo, status, itemId, page, size)));
    }

    @GetMapping("/inventory/export")
    @Operation(summary = "Export Inventory Report", description = "Streams the Inventory Report matching the "
            + "same filters as GET /inventory to a downloadable CSV, Excel, or PDF file.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportInventoryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "inventory-service TxnType enum name",
                    schema = @Schema(allowableValues = {"IN", "OUT"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID itemId,
            @RequestParam ExportFormat format) {
        return export("inventory-report", format, out ->
                reportService.exportInventoryReport(format, out, dateFrom, dateTo, status, itemId));
    }

    @GetMapping("/production")
    @Operation(summary = "Production Report", description = "Filters: dateRange (collectionDate), status "
            + "(qualityGrade), farm (resolved to that farm's cows before querying production-service, which "
            + "has no farm linkage of its own).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Production report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<ProductionReportRow, ProductionReportSummary>>> getProductionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "production-service QualityGrade enum name",
                    schema = @Schema(allowableValues = {"A", "B", "C"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Production report retrieved successfully",
                reportService.getProductionReport(dateFrom, dateTo, status, farmId, page, size)));
    }

    @GetMapping("/production/export")
    @Operation(summary = "Export Production Report", description = "Streams the Production Report matching the "
            + "same filters as GET /production to a downloadable CSV, Excel, or PDF file.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportProductionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "production-service QualityGrade enum name",
                    schema = @Schema(allowableValues = {"A", "B", "C"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID farmId,
            @RequestParam ExportFormat format) {
        return export("production-report", format, out ->
                reportService.exportProductionReport(format, out, dateFrom, dateTo, status, farmId));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Delivery Report", description = "Filters: dateRange (assignedAt), status, customer "
            + "(resolved to that customer's orders via order-service's own Sales Report before querying "
            + "delivery-service, which has no customer linkage of its own).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Delivery report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<DeliveryReportRow, DeliveryReportSummary>>> getDeliveryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "delivery-service AssignmentStatus enum name",
                    schema = @Schema(allowableValues = {"ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Delivery report retrieved successfully",
                reportService.getDeliveryReport(dateFrom, dateTo, status, customerId, page, size)));
    }

    @GetMapping("/deliveries/export")
    @Operation(summary = "Export Delivery Report", description = "Streams the Delivery Report matching the same "
            + "filters as GET /deliveries to a downloadable CSV, Excel, or PDF file.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportDeliveryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "delivery-service AssignmentStatus enum name",
                    schema = @Schema(allowableValues = {"ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam ExportFormat format) {
        return export("delivery-report", format, out ->
                reportService.exportDeliveryReport(format, out, dateFrom, dateTo, status, customerId));
    }

    @GetMapping("/payments")
    @Operation(summary = "Payment Report", description = "Filters: dateRange (createdAt), status, customer. "
            + "Proxies payment-service's own Payment Report endpoint.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Payment report retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<PaymentReportRow, PaymentReportSummary>>> getPaymentReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "payment-service PaymentStatus enum name",
                    schema = @Schema(allowableValues = {"PENDING", "SUCCESS", "FAILED", "REFUNDED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ApiConstants.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.success("Payment report retrieved successfully",
                reportService.getPaymentReport(dateFrom, dateTo, status, customerId, page, size)));
    }

    @GetMapping("/payments/export")
    @Operation(summary = "Export Payment Report", description = "Streams the Payment Report matching the same "
            + "filters as GET /payments to a downloadable CSV, Excel, or PDF file.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream in the requested format (CSV, Excel, or PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> exportPaymentReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "payment-service PaymentStatus enum name",
                    schema = @Schema(allowableValues = {"PENDING", "SUCCESS", "FAILED", "REFUNDED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam ExportFormat format) {
        return export("payment-report", format, out ->
                reportService.exportPaymentReport(format, out, dateFrom, dateTo, status, customerId));
    }

    // ── Export helper ───────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ExportAction {
        void run(java.io.OutputStream out) throws java.io.IOException;
    }

    /** Shared by every export* endpoint above: builds the download response (attachment
     *  headers, content type) and wraps {@code action} in a StreamingResponseBody so Spring MVC
     *  runs it on its async dispatch thread instead of the request-handling thread. */
    private ResponseEntity<StreamingResponseBody> export(String filenamePrefix, ExportFormat format, ExportAction action) {
        String filename = filenamePrefix + "-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = action::run;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }
}
