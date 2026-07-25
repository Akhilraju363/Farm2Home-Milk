package com.farm2home.delivery.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.request.UpdateRouteRequest;
import com.farm2home.delivery.dto.response.RouteResponse;
import com.farm2home.delivery.service.impl.DeliveryRouteServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/delivery/routes")
@Tag(name = "Delivery Routes", description = "Manage delivery routes")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryRouteController {

    private final DeliveryRouteServiceImpl routeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Create a delivery route (admin)")
    public ResponseEntity<ApiResponse<RouteResponse>> create(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Route created successfully", routeService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all active routes")
    public ResponseEntity<ApiResponse<Page<RouteResponse>>> findAll(
            @PageableDefault(size = 20, sort = "routeCode") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Routes retrieved successfully", routeService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get route by ID")
    public ResponseEntity<ApiResponse<RouteResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Route retrieved successfully", routeService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update route (admin)")
    public ResponseEntity<ApiResponse<RouteResponse>> update(@PathVariable UUID id,
                                                @RequestBody UpdateRouteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Route updated successfully", routeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Deactivate and soft-delete route (admin)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        routeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Route deleted successfully", null));
    }
}
