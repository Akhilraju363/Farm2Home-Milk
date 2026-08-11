package com.farm2home.delivery.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.request.UpdateRouteRequest;
import com.farm2home.delivery.dto.response.RouteResponse;
import com.farm2home.delivery.service.impl.DeliveryRouteServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery/routes")
@Tag(name = "Delivery Routes", description = "Manage delivery routes: creation, listing, lookup, updates, and soft-delete.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryRouteController {

    private final DeliveryRouteServiceImpl routeService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Create a delivery route (admin)",
            description = "routeCode is stored upper-cased regardless of the casing submitted, and must be "
                    + "unique among non-deleted routes - a duplicate is rejected with a 400. The new route is "
                    + "active by default.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Route created",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Route created successfully",
                                  "data": {
                                    "id": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeName": "North Zone Route 1",
                                    "routeCode": "NZ-01",
                                    "area": "Whitefield",
                                    "city": "Bengaluru",
                                    "pincode": "560066",
                                    "active": true,
                                    "createdAt": "2026-07-31T09:00:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failure (missing/too-long routeName, routeCode, area, city, or an "
                        + "out-of-range pincode), or routeCode already exists", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content)
    })
    public ResponseEntity<ApiResponse<RouteResponse>> create(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Route created successfully", routeService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all routes",
            description = "Paginated list of all non-deleted routes, sorted by routeCode by default. Not "
                    + "filtered by the active flag - despite this endpoint's original \"list all active "
                    + "routes\" description, DeliveryRouteServiceImpl.findAll() queries "
                    + "findAllByDeletedFalse(...), so inactive-but-not-deleted routes are included too; a "
                    + "separate findAllByActiveTrueAndDeletedFalse() repository method exists but is unused. "
                    + "Open to any authenticated caller.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Routes retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<RouteResponse>>> findAll(
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "routeCode") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Routes retrieved successfully", routeService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get route by ID",
            description = "No role restriction beyond authentication - any authenticated caller may look up "
                    + "any non-deleted route by id.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Route retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Route retrieved successfully",
                                  "data": {
                                    "id": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeName": "North Zone Route 1",
                                    "routeCode": "NZ-01",
                                    "area": "Whitefield",
                                    "city": "Bengaluru",
                                    "pincode": "560066",
                                    "active": true,
                                    "createdAt": "2026-07-31T09:00:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Route not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<ApiResponse<RouteResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Route retrieved successfully", routeService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update route (admin)",
            description = "Partial update - only non-null fields are applied (see UpdateRouteRequest); "
                    + "routeCode cannot be changed via this endpoint. Bean-validation constraints (field "
                    + "lengths, pincode length) are enforced via @Valid.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Route updated",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Route updated successfully",
                                  "data": {
                                    "id": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeName": "North Zone Route 1 (Revised)",
                                    "routeCode": "NZ-01",
                                    "area": "Whitefield",
                                    "city": "Bengaluru",
                                    "pincode": "560066",
                                    "active": true,
                                    "createdAt": "2026-07-31T09:00:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failure (routeName/area/city too long, pincode outside 6-10 characters)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Route not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<ApiResponse<RouteResponse>> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateRouteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Route updated successfully", routeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Soft-delete route (admin)",
            description = "Marks the route as deleted (route.deleted = true) so it drops out of GET / and "
                    + "GET /{id}. This is a soft delete only - the row is not removed, and despite the prior "
                    + "summary's wording (\"Deactivate and soft-delete\"), it does NOT touch the separate "
                    + "`active` flag; DeliveryRouteServiceImpl.delete() sets deleted=true only.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Route soft-deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Route not found or already soft-deleted", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        routeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Route deleted successfully", null));
    }
}
