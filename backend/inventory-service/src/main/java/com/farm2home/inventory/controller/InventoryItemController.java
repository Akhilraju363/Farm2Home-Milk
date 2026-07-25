package com.farm2home.inventory.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.request.UpdateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.service.impl.InventoryItemServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryItemController {

    private final InventoryItemServiceImpl service;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> create(
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inventory item created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<InventoryItemResponse>>> findAll(
            @RequestParam(required = false) ItemType type, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Inventory items retrieved successfully", service.findAll(type, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory item retrieved successfully", service.findById(id)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> lowStock() {
        return ResponseEntity.ok(ApiResponse.success("Low-stock items retrieved successfully", service.findLowStock()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Inventory item updated successfully", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Inventory item deleted successfully", null));
    }
}
