package com.farm2home.farm.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.farm.domain.enums.CowStatus;
import com.farm2home.farm.dto.request.CreateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowStatusRequest;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.service.impl.CowServiceImpl;
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
@RequestMapping("/api/v1/farm/cows")
@Tag(name = "Cows", description = "Cow registry management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CowController {

    private final CowServiceImpl cowService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Register a new cow")
    public ResponseEntity<ApiResponse<CowResponse>> create(@Valid @RequestBody CreateCowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cow registered successfully", cowService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all cows (optional status filter)")
    public ResponseEntity<ApiResponse<Page<CowResponse>>> findAll(
            @RequestParam(required = false) CowStatus status,
            @PageableDefault(size = 20, sort = "tagNumber") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Cows retrieved successfully", cowService.findAll(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cow by ID")
    public ResponseEntity<ApiResponse<CowResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Cow retrieved successfully", cowService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update cow details")
    public ResponseEntity<ApiResponse<CowResponse>> update(@PathVariable UUID id,
                                               @RequestBody UpdateCowRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cow updated successfully", cowService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Update cow status (ACTIVE/SICK/SOLD/DECEASED)")
    public ResponseEntity<ApiResponse<CowResponse>> updateStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCowStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cow status updated successfully", cowService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a cow record")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        cowService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Cow deleted successfully", null));
    }
}
