package com.farm2home.delivery.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.UpdatePartnerRequest;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.service.impl.DeliveryPartnerServiceImpl;
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
@RequestMapping("/api/v1/delivery/partners")
@Tag(name = "Delivery Partners", description = "Manage delivery partners")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryPartnerController {

    private final DeliveryPartnerServiceImpl partnerService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Register a delivery partner (admin)")
    public ResponseEntity<ApiResponse<PartnerResponse>> create(@Valid @RequestBody CreatePartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery partner created successfully", partnerService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "List all delivery partners (admin)")
    public ResponseEntity<ApiResponse<Page<PartnerResponse>>> findAll(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Delivery partners retrieved successfully", partnerService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get partner by ID")
    public ResponseEntity<ApiResponse<PartnerResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Delivery partner retrieved successfully", partnerService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update partner (admin)")
    public ResponseEntity<ApiResponse<PartnerResponse>> update(@PathVariable UUID id,
                                                   @RequestBody UpdatePartnerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Delivery partner updated successfully", partnerService.update(id, request)));
    }
}
