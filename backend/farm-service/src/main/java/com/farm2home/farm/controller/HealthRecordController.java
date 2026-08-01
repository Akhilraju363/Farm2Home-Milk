package com.farm2home.farm.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.farm.dto.request.CreateHealthRecordRequest;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.service.impl.HealthRecordServiceImpl;
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

import java.util.UUID;

/**
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention used across the platform's other controllers.
 */
@RestController
@RequestMapping("/api/v1/farm/cows/{cowId}/health")
@Tag(name = "Health Records", description = "Cow health record management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class HealthRecordController {

    private final HealthRecordServiceImpl healthRecordService;

    @PostMapping
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Add a health record for a cow",
            description = "Creates a new health record attached to the given cow. The cow must exist and not be "
                    + "soft-deleted (404 otherwise).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Health record added"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing recordDate/condition, recordDate in the future, "
                        + "or a text field exceeds its max length)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given cowId", content = @Content)
    })
    public ResponseEntity<ApiResponse<HealthRecordResponse>> create(
            @Parameter(description = "Id of the parent cow this health record belongs to") @PathVariable UUID cowId,
            @Valid @RequestBody CreateHealthRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Health record added successfully", healthRecordService.create(cowId, request)));
    }

    @GetMapping
    @Operation(summary = "Get full health history for a cow",
            description = "Returns the cow's non-deleted health records, most recent recordDate first. The cow "
                    + "must exist and not be soft-deleted (404 otherwise).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Health records retrieved (possibly an empty page if the cow has none)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given cowId", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<HealthRecordResponse>>> findAll(
            @Parameter(description = "Id of the parent cow this health record belongs to") @PathVariable UUID cowId,
            @Parameter(description = "Standard Spring page/size/sort request parameters")
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Health records retrieved successfully", healthRecordService.findByCow(cowId, pageable)));
    }

    @GetMapping("/latest")
    @Operation(summary = "Get most recent health record for a cow",
            description = "Returns the cow's single most recent non-deleted health record by recordDate. Returns "
                    + "404 both when the cow itself doesn't exist and when the cow exists but has no health "
                    + "records yet.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Latest health record retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given cowId, or the cow has no health records",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<HealthRecordResponse>> latest(
            @Parameter(description = "Id of the parent cow this health record belongs to") @PathVariable UUID cowId) {
        return ResponseEntity.ok(ApiResponse.success("Latest health record retrieved successfully", healthRecordService.findLatest(cowId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Soft-delete a health record",
            description = "Marks the health record as deleted (deleted=true) rather than removing the row. "
                    + "Requires the record to belong to the given cowId.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Health record deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted health record with the given id exists for the given cowId",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Id of the parent cow this health record belongs to") @PathVariable UUID cowId,
            @Parameter(description = "Id of the health record to delete") @PathVariable UUID id) {
        healthRecordService.delete(cowId, id);
        return ResponseEntity.ok(ApiResponse.success("Health record deleted successfully", null));
    }
}
