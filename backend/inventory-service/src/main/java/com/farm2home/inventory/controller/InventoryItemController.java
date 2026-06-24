package com.farm2home.inventory.controller;

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
    public ResponseEntity<InventoryItemResponse> create(
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<InventoryItemResponse>> findAll(
            @RequestParam(required = false) ItemType type, Pageable pageable) {
        return ResponseEntity.ok(service.findAll(type, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InventoryItemResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InventoryItemResponse>> lowStock() {
        return ResponseEntity.ok(service.findLowStock());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<InventoryItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
