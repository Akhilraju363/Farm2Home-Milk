package com.farm2home.farm.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.farm.dto.request.CreateFarmRequest;
import com.farm2home.farm.dto.request.UpdateFarmRequest;
import com.farm2home.farm.dto.response.FarmResponse;
import com.farm2home.farm.service.impl.FarmServiceImpl;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention used across the platform's other controllers.
 */
@RestController
@RequestMapping("/api/v1/farm")
@Tag(name = "Farms", description = "Farm profile management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FarmController {

    private final FarmServiceImpl farmService;

    @PostMapping
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Register a new farm",
            description = "Creates a new farm profile. No image is attached at creation time - upload one "
                    + "separately via POST /{id}/image.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Farm registered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing farmName/ownerName or a field exceeds its max length)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content)
    })
    public ResponseEntity<ApiResponse<FarmResponse>> create(@Valid @RequestBody CreateFarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Farm registered successfully", farmService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all farms",
            description = "Returns non-deleted farms, sorted by farm name by default. No filters - use "
                    + "GET /search for keyword/date filtering.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Farms retrieved (possibly an empty page)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<FarmResponse>>> findAll(
            @Parameter(description = "Standard Spring page/size/sort request parameters")
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "farmName") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Farms retrieved successfully", farmService.findAll(pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search farms", description = "Keyword search across farm name/owner name/location/description, "
            + "plus an optional created-date range filter. All filters are optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Farms retrieved (possibly an empty page if nothing matches)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<FarmResponse>>> search(
            @Parameter(description = "Matches farm name, owner name, location, or description")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Inclusive lower bound on farm creation date (ISO yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Inclusive upper bound on farm creation date (ISO yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Standard Spring page/size/sort request parameters")
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "farmName") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Farms retrieved successfully",
                farmService.search(keyword, dateFrom, dateTo, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get farm by ID",
            description = "Returns a single farm by id. 404 if the farm does not exist or has been soft-deleted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Farm retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted farm exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<FarmResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Farm retrieved successfully", farmService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update farm details",
            description = "Partially updates a farm's profile fields (name, owner name, location, description). "
                    + "Fields omitted/null in the request are left unchanged - only non-null fields overwrite the "
                    + "existing record (see FarmMapper#updateFarmFromRequest, mapped with "
                    + "NullValuePropertyMappingStrategy.IGNORE). The farm image is not touched by this endpoint - "
                    + "use POST /{id}/image to change it.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Farm updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "A supplied field violates its validation constraint (e.g. farmName/location too long)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted farm exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<FarmResponse>> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateFarmRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Farm updated successfully", farmService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Soft-delete a farm record",
            description = "Marks the farm as deleted (deleted=true) rather than removing the row. Soft-deleted "
                    + "farms are excluded from all other farm lookups but remain in the database for audit "
                    + "purposes. Cows already assigned to this farm are not cascade-deleted or reassigned.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Farm deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted farm exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        farmService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Farm deleted successfully", null));
    }

    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Upload/replace the farm's image",
            description = "Stores the uploaded image and sets it as the farm's imageUrl, replacing any previous "
                    + "image. Constraints are centrally configured on FileStorageProperties (app.upload.* - "
                    + "defaults: 5 MB max size, image/jpeg, image/png, and image/webp content types only); an "
                    + "empty file or a file breaching either limit is rejected with 400.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Farm image uploaded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "File is missing/empty, exceeds the configured max size, or has an unsupported content type",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted farm exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<FarmResponse>> uploadImage(
            @PathVariable UUID id,
            @Parameter(description = "Image file (jpeg/png/webp, max 5 MB by default)", required = true)
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Farm image uploaded successfully",
                farmService.uploadImage(id, file)));
    }
}
