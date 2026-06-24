package com.farm2home.farm.controller;

import com.farm2home.farm.dto.request.CreateHealthRecordRequest;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.service.impl.HealthRecordServiceImpl;
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
@RequestMapping("/api/v1/farm/cows/{cowId}/health")
@Tag(name = "Health Records", description = "Cow health record management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class HealthRecordController {

    private final HealthRecordServiceImpl healthRecordService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Add a health record for a cow")
    public ResponseEntity<HealthRecordResponse> create(@PathVariable UUID cowId,
                                                        @Valid @RequestBody CreateHealthRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthRecordService.create(cowId, request));
    }

    @GetMapping
    @Operation(summary = "Get full health history for a cow")
    public ResponseEntity<Page<HealthRecordResponse>> findAll(
            @PathVariable UUID cowId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(healthRecordService.findByCow(cowId, pageable));
    }

    @GetMapping("/latest")
    @Operation(summary = "Get most recent health record for a cow")
    public ResponseEntity<HealthRecordResponse> latest(@PathVariable UUID cowId) {
        return ResponseEntity.ok(healthRecordService.findLatest(cowId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a health record")
    public ResponseEntity<Void> delete(@PathVariable UUID cowId, @PathVariable UUID id) {
        healthRecordService.delete(cowId, id);
        return ResponseEntity.noContent().build();
    }
}
