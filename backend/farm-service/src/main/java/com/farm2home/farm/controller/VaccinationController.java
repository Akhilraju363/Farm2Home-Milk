package com.farm2home.farm.controller;

import com.farm2home.farm.dto.request.CreateVaccinationRequest;
import com.farm2home.farm.dto.response.VaccinationResponse;
import com.farm2home.farm.service.impl.VaccinationServiceImpl;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farm")
@Tag(name = "Vaccinations", description = "Cow vaccination tracking")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationServiceImpl vaccinationService;

    @PostMapping("/cows/{cowId}/vaccinations")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Record a vaccination for a cow")
    public ResponseEntity<VaccinationResponse> create(@PathVariable UUID cowId,
                                                       @Valid @RequestBody CreateVaccinationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vaccinationService.create(cowId, request));
    }

    @GetMapping("/cows/{cowId}/vaccinations")
    @Operation(summary = "Get vaccination history for a cow")
    public ResponseEntity<Page<VaccinationResponse>> findByCow(
            @PathVariable UUID cowId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(vaccinationService.findByCow(cowId, pageable));
    }

    @GetMapping("/vaccinations/upcoming")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Get upcoming vaccinations in the next N days (default 7)")
    public ResponseEntity<List<VaccinationResponse>> upcoming(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(vaccinationService.findUpcoming(days));
    }

    @DeleteMapping("/cows/{cowId}/vaccinations/{id}")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a vaccination record")
    public ResponseEntity<Void> delete(@PathVariable UUID cowId, @PathVariable UUID id) {
        vaccinationService.delete(cowId, id);
        return ResponseEntity.noContent().build();
    }
}
