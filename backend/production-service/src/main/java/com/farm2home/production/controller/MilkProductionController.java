package com.farm2home.production.controller;

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
    public ResponseEntity<MilkProductionResponse> create(
            @Valid @RequestBody CreateMilkProductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<MilkProductionResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MilkProductionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/cow/{cowId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<MilkProductionResponse>> findByCow(
            @PathVariable UUID cowId, Pageable pageable) {
        return ResponseEntity.ok(service.findByCow(cowId, pageable));
    }

    @GetMapping("/cow/{cowId}/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DailySummaryResponse>> cowSummary(
            @PathVariable UUID cowId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.getDailySummaryByCow(cowId, from, to));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DailySummaryResponse>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.getDailySummary(from, to));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MilkProductionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMilkProductionRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
