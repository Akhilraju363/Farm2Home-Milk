package com.farm2home.customer.controller;

import com.farm2home.common.core.analytics.CustomerGrowthPoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.customer.domain.enums.CustomerStatus;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.CustomerResponse;
import com.farm2home.customer.service.impl.CustomerServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer profile management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerServiceImpl customerService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "List all customers",
            description = "Unfiltered, paginated list of every non-deleted customer, most recently created "
                    + "first by default. For keyword/date/status filtering use GET /search instead.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customers retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> findAll(
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved successfully", customerService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer profile by ID",
            description = "Returns a single customer's profile. Soft-deleted customers are never returned "
                    + "(404, same as an unknown id).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id (or it is soft-deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Customer retrieved successfully", customerService.findById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer profile",
            description = "Partial update of a customer's own profile fields (firstName, lastName, email, "
                    + "status). Any field left null in the request body is left unchanged - this is not a "
                    + "full replace. Fields such as mobile and customerCode are immutable and not settable here.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. firstName/lastName outside 2-100 chars, or a malformed email)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id (or it is soft-deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", customerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Soft-delete a customer profile",
            description = "Marks the customer as deleted (excluded from every subsequent find/search/report/"
                    + "export) without removing the row. Blocked while the customer's status is ACTIVE - the "
                    + "caller must set status to INACTIVE or SUSPENDED via PUT /{id} first (see "
                    + "CustomerServiceImpl.delete).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer soft-deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id (or it is already soft-deleted)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                description = "Customer status is still ACTIVE - set it to INACTIVE or SUSPENDED before deleting",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", null));
    }

    @PostMapping(value = "/{id}/profile-image", consumes = "multipart/form-data")
    @Operation(summary = "Upload/replace the customer's profile image",
            description = "Stores the uploaded file and overwrites profileImageUrl on the customer's profile; "
                    + "any previous image at the old path is not deleted. The file is validated for non-empty "
                    + "content, an allowed content type, and a maximum size (see FileStorageService/"
                    + "FileStorageProperties for the configured allow-list and byte limit).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Profile image stored; returns the customer with the new profileImageUrl"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "File missing/empty, content type not in the allowed list, or file exceeds the "
                        + "configured maximum size", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id (or it is soft-deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerResponse>> uploadProfileImage(
            @PathVariable UUID id,
            @Parameter(description = "Image file to store (see FileStorageProperties for allowed content types and max size)")
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Profile image uploaded successfully",
                customerService.uploadProfileImage(id, file)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get customer summary metrics for the dashboard",
            description = "Currently returns just the total non-deleted customer count (see "
                    + "CustomerSummaryResponse/CustomerServiceImpl.getSummary).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer summary retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Customer summary retrieved successfully", customerService.getSummary()));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Search customers",
            description = "Keyword search across name/mobile/email/customer code, plus optional date range "
                    + "and status filters. All filters are optional and combine with AND.")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> search(
            @Parameter(description = "Matches name, mobile, email, or customer code") @RequestParam(required = false) String keyword,
            @Parameter(description = "Created date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Created date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved successfully",
                customerService.search(keyword, dateFrom, dateTo, status, pageable)));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Customer Report",
            description = "Filtered, paginated customers plus summary totals (count by status). "
                    + "All filters are optional and combine with AND.")
    public ResponseEntity<ApiResponse<ReportPage<CustomerReportRow, CustomerReportSummary>>> getReport(
            @Parameter(description = "Created date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Created date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Customer report retrieved successfully",
                customerService.getReport(dateFrom, dateTo, status, pageable)));
    }

    @GetMapping("/analytics/growth-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Customer Growth", description = "Count of new customer signups bucketed by the "
            + "requested granularity (day/week/month/year). dateFrom/dateTo are optional and bound createdAt; "
            + "the GROUP BY/COUNT aggregation runs in the database.")
    public ResponseEntity<ApiResponse<TrendSeries<CustomerGrowthPoint>>> getGrowthTrend(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Customer growth trend retrieved successfully",
                customerService.getGrowthTrend(granularity, dateFrom, dateTo)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Export customers", description = "Streams customers matching the same filters as "
            + "GET /search to a downloadable CSV, Excel, or PDF file. Runs on Spring MVC's async dispatch "
            + "thread (via StreamingResponseBody) and fetches rows in bounded pages, so large exports don't "
            + "block a request-handling thread or require holding the full result set in memory.")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam ExportFormat format,
            @Parameter(description = "Matches name, mobile, email, or customer code") @RequestParam(required = false) String keyword,
            @Parameter(description = "Created date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Created date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {
        String filename = "customers-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = out ->
                customerService.export(format, out, keyword, dateFrom, dateTo, status, sortBy, ascending);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }
}
