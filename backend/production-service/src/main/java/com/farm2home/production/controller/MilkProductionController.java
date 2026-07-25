package com.farm2home.production.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.production.config.UserPrincipal;
import com.farm2home.production.dto.request.CreateMilkProductionRequest;
import com.farm2home.production.dto.request.UpdateMilkProductionRequest;
import com.farm2home.production.dto.response.DailySummaryResponse;
import com.farm2home.production.dto.response.MilkProductionResponse;
import com.farm2home.production.service.impl.MilkProductionServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productions")
@RequiredArgsConstructor
public class MilkProductionController {

    private final MilkProductionServiceImpl service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MilkProductionResponse>> create(
            @Valid @RequestBody CreateMilkProductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Milk production record created successfully", service.create(request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<MilkProductionResponse>>> findAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Milk production records retrieved successfully", service.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MilkProductionResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Milk production record retrieved successfully", service.findById(id)));
    }

    @GetMapping("/cow/{cowId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<MilkProductionResponse>>> findByCow(
            @PathVariable UUID cowId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Milk production records for cow retrieved successfully", service.findByCow(cowId, pageable)));
    }

    @GetMapping("/cow/{cowId}/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DailySummaryResponse>>> cowSummary(
            @PathVariable UUID cowId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Cow daily summary retrieved successfully", service.getDailySummaryByCow(cowId, from, to)));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DailySummaryResponse>>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Daily summary retrieved successfully", service.getDailySummary(from, to)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MilkProductionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMilkProductionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Milk production record updated successfully", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Milk production record deleted successfully", null));
    }
}
