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
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.domain.enums.CustomerStatus;
import com.farm2home.customer.dto.request.CreateAddressRequest;
import com.farm2home.customer.dto.request.UpdateAddressRequest;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.AddressResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
                    + "(404, same as an unknown id). Callers may only fetch their own profile unless they hold "
                    + "SUPER_ADMIN/DELIVERY_MANAGER - a mismatched id gets the same 404 as an unknown one, never "
                    + "a 403 that would confirm the id exists.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id, it is soft-deleted, or it belongs to someone else",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerResponse>> findById(@PathVariable UUID id,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Customer retrieved successfully", customerService.findById(id, principal)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer profile",
            description = "Partial update of a customer's own profile fields (firstName, lastName, email, "
                    + "status). Any field left null in the request body is left unchanged - this is not a "
                    + "full replace. Fields such as mobile and customerCode are immutable and not settable here. "
                    + "Callers may only update their own profile unless they hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Customer updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. firstName/lastName outside 2-100 chars, or a malformed email)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id, it is soft-deleted, or it belongs to someone else",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCustomerRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", customerService.update(id, request, principal)));
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
                    + "FileStorageProperties for the configured allow-list and byte limit). Callers may only "
                    + "upload their own image unless they hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Profile image stored; returns the customer with the new profileImageUrl"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "File missing/empty, content type not in the allowed list, or file exceeds the "
                        + "configured maximum size", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id, it is soft-deleted, or it belongs to someone else",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<CustomerResponse>> uploadProfileImage(
            @PathVariable UUID id,
            @Parameter(description = "Image file to store (see FileStorageProperties for allowed content types and max size)")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Profile image uploaded successfully",
                customerService.uploadProfileImage(id, file, principal)));
    }

    @PostMapping("/me/addresses")
    @Operation(summary = "Add a delivery address for the current customer",
            description = "Self-service: saves a delivery address against the caller's own customer profile "
                    + "(customer id is taken from the bearer token, not the request body — there is no way to "
                    + "add an address to a different customer here). The underlying customer row is created "
                    + "asynchronously from auth-service's register() call via a Kafka event "
                    + "(CustomerEventConsumer); calling this endpoint immediately after registering may 404 "
                    + "briefly until that event is consumed — retry a few times with a short backoff rather "
                    + "than treating it as a hard failure.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Address saved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (missing city/state/address line, or pincode not 6 digits)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Customer profile not created yet (registration Kafka event not yet consumed) — "
                        + "retry shortly", content = @Content)
    })
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @Valid @RequestBody CreateAddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address saved successfully", customerService.addAddress(principal.userId(), request)));
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "List a customer's saved addresses",
            description = "Returns every non-deleted delivery address for the given customer, most-recently-"
                    + "added order is not guaranteed. Callers may only list their own addresses unless they "
                    + "hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Addresses retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id, it is soft-deleted, or it belongs to someone else",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Addresses retrieved successfully", customerService.getAddresses(id, principal)));
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add a delivery address for a customer",
            description = "Admin/ownership-scoped counterpart to POST /me/addresses - lets an authorized admin "
                    + "add an address on behalf of a specific customer. The first address added for a customer "
                    + "becomes their default automatically; later ones don't. Callers may only add to their own "
                    + "profile unless they hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Address saved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (missing city/state/address line, or pincode not 6 digits)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No customer with this id, it is soft-deleted, or it belongs to someone else",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<AddressResponse>> addAddressForCustomer(
            @PathVariable UUID id, @Valid @RequestBody CreateAddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address saved successfully", customerService.addAddressScoped(id, request, principal)));
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Update a customer's delivery address",
            description = "Partial update - any field left null in the request body is left unchanged. "
                    + "Callers may only update their own addresses unless they hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Address updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. pincode not 6 digits)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No matching customer or address (or either belongs to someone else)",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable UUID id, @PathVariable UUID addressId,
            @Valid @RequestBody UpdateAddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully",
                customerService.updateAddress(id, addressId, request, principal)));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Delete a customer's delivery address",
            description = "Soft-deletes the address; it stops appearing in GET /{id}/addresses. Callers may "
                    + "only delete their own addresses unless they hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Address deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No matching customer or address (or either belongs to someone else)",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable UUID id, @PathVariable UUID addressId,
            @AuthenticationPrincipal UserPrincipal principal) {
        customerService.deleteAddress(id, addressId, principal);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully", null));
    }

    @PatchMapping("/{id}/addresses/{addressId}/default")
    @Operation(summary = "Set a customer's default delivery address",
            description = "Marks this address as the customer's default and unsets the flag on every other "
                    + "one of their addresses, so exactly one stays default. Callers may only change their own "
                    + "addresses unless they hold SUPER_ADMIN/DELIVERY_MANAGER.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Default address updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No matching customer or address (or either belongs to someone else)",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @PathVariable UUID id, @PathVariable UUID addressId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Default address updated successfully",
                customerService.setDefaultAddress(id, addressId, principal)));
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
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER
            + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "')")
    @Operation(summary = "Search customers",
            description = "Keyword search across name/mobile/email/customer code, plus optional date range "
                    + "and status filters. All filters are optional and combine with AND. FARM_MANAGER included "
                    + "(alongside SUPER_ADMIN/DELIVERY_MANAGER) so they can look up a customer while creating/"
                    + "managing a subscription on that customer's behalf - see UserPrincipal.isAdmin().")
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
