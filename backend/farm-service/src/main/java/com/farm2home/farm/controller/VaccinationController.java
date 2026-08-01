package com.farm2home.farm.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.farm.dto.request.CreateVaccinationRequest;
import com.farm2home.farm.dto.request.UpdateVaccinationRequest;
import com.farm2home.farm.dto.response.VaccinationResponse;
import com.farm2home.farm.service.impl.VaccinationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

/**
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention used across the platform's other controllers.
 */
@RestController
@RequestMapping("/api/v1/farm")
@Tag(name = "Vaccinations", description = "Cow vaccination tracking")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationServiceImpl vaccinationService;

    @PostMapping("/cows/{cowId}/vaccinations")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Record a vaccination for a cow",
            description = "Creates a new vaccination record attached to the given cow. `nextDueDate`, if supplied, "
                    + "drives the GET /vaccinations/upcoming reminder list. The cow must exist and not be "
                    + "soft-deleted (404 otherwise).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vaccination recorded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing vaccineName/administeredAt, administeredAt in the "
                        + "future, or a text field exceeds its max length)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given cowId", content = @Content)
    })
    public ResponseEntity<ApiResponse<VaccinationResponse>> create(
            @Parameter(description = "Id of the parent cow this vaccination belongs to") @PathVariable UUID cowId,
            @Valid @RequestBody CreateVaccinationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vaccination recorded successfully", vaccinationService.create(cowId, request)));
    }

    @GetMapping("/cows/{cowId}/vaccinations")
    @Operation(summary = "Get vaccination history for a cow",
            description = "Returns the cow's non-deleted vaccination records, most recent administeredAt first. "
                    + "The cow must exist and not be soft-deleted (404 otherwise).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Vaccination history retrieved (possibly an empty page)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given cowId", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<VaccinationResponse>>> findByCow(
            @Parameter(description = "Id of the parent cow this vaccination belongs to") @PathVariable UUID cowId,
            @Parameter(description = "Standard Spring page/size/sort request parameters")
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Vaccination history retrieved successfully", vaccinationService.findByCow(cowId, pageable)));
    }

    @PutMapping("/cows/{cowId}/vaccinations/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update a vaccination record",
            description = "Partially updates a vaccination record's fields. Fields omitted/null in the request "
                    + "are left unchanged - only non-null fields overwrite the existing record (see "
                    + "FarmMapper#updateVaccinationFromRequest, mapped with "
                    + "NullValuePropertyMappingStrategy.IGNORE). Requires the record to belong to the given cowId.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vaccination record updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "A supplied field violates its validation constraint (e.g. administeredAt in the "
                        + "future, or a text field exceeds its max length)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted vaccination record with the given id exists for the given cowId",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<VaccinationResponse>> update(
            @Parameter(description = "Id of the parent cow this vaccination belongs to") @PathVariable UUID cowId,
            @Parameter(description = "Id of the vaccination record to update") @PathVariable UUID id,
            @Valid @RequestBody UpdateVaccinationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vaccination record updated successfully",
                vaccinationService.update(cowId, id, request)));
    }

    @GetMapping("/vaccinations/upcoming")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Get upcoming vaccinations in the next N days (default 7)",
            description = "Platform-wide (not scoped to a single cow) list of non-deleted vaccination records "
                    + "whose nextDueDate falls between today and today + `days` inclusive. `days` defaults to 7 "
                    + "when omitted; there is no cap enforced on the value supplied.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Upcoming vaccinations retrieved (possibly empty if none are due in the window)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<VaccinationResponse>>> upcoming(
            @Parameter(description = "Look-ahead window in days from today (inclusive); defaults to 7")
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.success("Upcoming vaccinations retrieved successfully", vaccinationService.findUpcoming(days)));
    }

    @DeleteMapping("/cows/{cowId}/vaccinations/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Soft-delete a vaccination record",
            description = "Marks the vaccination record as deleted (deleted=true) rather than removing the row, "
                    + "and writes an audit log entry. Requires the record to belong to the given cowId.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vaccination record deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted vaccination record with the given id exists for the given cowId",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Id of the parent cow this vaccination belongs to") @PathVariable UUID cowId,
            @Parameter(description = "Id of the vaccination record to delete") @PathVariable UUID id) {
        vaccinationService.delete(cowId, id);
        return ResponseEntity.ok(ApiResponse.success("Vaccination record deleted successfully", null));
    }
}
