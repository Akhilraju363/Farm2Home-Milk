package com.farm2home.inventory.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.dto.request.CreateProductCategoryRequest;
import com.farm2home.inventory.dto.request.UpdateProductCategoryRequest;
import com.farm2home.inventory.dto.response.ProductCategoryResponse;
import com.farm2home.inventory.service.impl.ProductCategoryServiceImpl;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/product-categories")
@Tag(name = "Product Categories", description = "Relational taxonomy for the sellable product catalog - "
        + "distinct from any farm-supply/InventoryItem classification.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryServiceImpl service;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Create product category",
            description = "Registers a new product category. Categories are active by default; there is no "
                    + "`active` field on create - use the update endpoint to deactivate one later.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Category created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing name)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                description = "A category with this name already exists", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> create(
            @Valid @RequestBody CreateProductCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product category created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List product categories",
            description = "Paginated category list. By default only active, non-deleted categories are "
                    + "returned; pass activeOnly=false to include inactive categories too. Soft-deleted "
                    + "categories are always excluded.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Categories retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<ProductCategoryResponse>>> findAll(
            @Parameter(description = "When true (default), only active categories are returned")
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Product categories retrieved successfully",
                service.findAll(activeOnly, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get product category by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Category retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No category with this ID (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Product category retrieved successfully", service.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update product category",
            description = "Partial update of a category's name/description/active flag - any field left null "
                    + "in the request body is left unchanged. Setting active=false is how a category is "
                    + "deactivated (existing products keep their reference; it just stops accepting new "
                    + "assignments).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Category updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No category with this ID (or it has been deleted)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                description = "Another category with the new name already exists", content = @Content)
    })
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateProductCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product category updated successfully", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Delete product category",
            description = "Soft-deletes the category (sets a deleted flag); it stops appearing in listings and "
                    + "lookups, and can no longer be assigned to products. Existing products that already "
                    + "reference it keep their categoryId/categoryName as-is.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Category deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller is not FARM_MANAGER or SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No category with this ID (or it has already been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product category deleted successfully", null));
    }
}
