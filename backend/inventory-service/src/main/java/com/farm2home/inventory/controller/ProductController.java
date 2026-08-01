package com.farm2home.inventory.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.dto.request.CreateProductRequest;
import com.farm2home.inventory.dto.request.UpdateProductRequest;
import com.farm2home.inventory.dto.response.ProductResponse;
import com.farm2home.inventory.service.impl.ProductServiceImpl;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/products")
@Tag(name = "Products", description = "Sellable milk/dairy product catalog - definitions, pricing, active status, "
        + "and product images. Distinct from Inventory Items (raw farm supplies like feed/medicine).")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProductController {

    private final ProductServiceImpl service;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Create product",
            description = "Registers a new sellable product. Products are active by default; there is no "
                    + "`active` field on create - use the update endpoint to deactivate one later.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Product created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing name, or price is missing/not greater than zero)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List products",
            description = "Paginated product catalog. By default only active, non-deleted products are returned; "
                    + "pass activeOnly=false to include inactive products too. Soft-deleted products are always excluded.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Products retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> findAll(
            @Parameter(description = "When true (default), only active products are returned")
            @RequestParam(defaultValue = "true") boolean activeOnly, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", service.findAll(activeOnly, pageable)));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search products", description = "Keyword search across name/description/category, plus "
            + "optional date range and active-status filters. All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Products retrieved (possibly empty if no product matches the filters)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> search(
            @Parameter(description = "Matches name, description, or category") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully",
                service.search(keyword, dateFrom, dateTo, active, pageable)));
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export products", description = "Streams products matching the same filters as "
            + "GET /search to a downloadable CSV, Excel, or PDF file. Runs on Spring MVC's async dispatch "
            + "thread (via StreamingResponseBody) and fetches rows in bounded pages, so large exports don't "
            + "block a request-handling thread or require holding the full result set in memory.")
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
            @Parameter(description = "Matches name, description, or category") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {
        String filename = "products-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = out ->
                service.export(format, out, keyword, dateFrom, dateTo, active, sortBy, ascending);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get product by ID",
            description = "Fetches a single product, including its current active status and image URL if one "
                    + "has been uploaded.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Product retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Product retrieved successfully",
                                  "data": {
                                    "id": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "name": "Full Cream Milk 1L",
                                    "description": "Farm-fresh full cream milk, pasteurized",
                                    "category": "Milk",
                                    "price": 65.00,
                                    "imageUrl": "/uploads/products/3b1e6a2c-full-cream-1l.jpg",
                                    "active": true,
                                    "createdAt": "2026-06-01T08:30:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No product with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", service.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update product",
            description = "Partial update of a product's name/description/category/price/active flag - any "
                    + "field left null in the request body is left unchanged (see "
                    + "ProductMapper.updateEntityFromRequest, which uses MapStruct's null-ignore strategy). "
                    + "Setting active=false is how a product is deactivated/hidden from customers.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Product updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. price present but not greater than zero)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No product with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Delete product",
            description = "Soft-deletes the product (sets a deleted flag); it stops appearing in listings, "
                    + "search, and lookups.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Product deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No product with this ID (or it has already been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }

    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Upload product image",
            description = "Uploads a new image file for the product, replacing any existing image URL. The file "
                    + "is stored via FileStorageService and the product's imageUrl is set to `/uploads/{path}`.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Image uploaded and product updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "file part missing or empty", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No product with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductResponse>> uploadImage(
            @PathVariable UUID id,
            @Parameter(description = "Image file (multipart/form-data)") @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Product image uploaded successfully",
                service.uploadImage(id, file)));
    }
}
