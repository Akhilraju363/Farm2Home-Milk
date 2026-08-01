package com.farm2home.farm.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.farm.domain.enums.CowStatus;
import com.farm2home.farm.dto.request.CreateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowStatusRequest;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.service.impl.CowServiceImpl;
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
@RequestMapping("/api/v1/farm/cows")
@Tag(name = "Cows", description = "Cow registry management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CowController {

    private final CowServiceImpl cowService;

    @PostMapping
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Register a new cow",
            description = "Creates a new cow record. `tagNumber` must be unique among non-deleted cows (case is "
                    + "normalized to upper-case on save); registering a second cow with the same tag number is "
                    + "rejected. The cow is created without a status filter applied - it starts as ACTIVE.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Cow registered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. missing tagNumber/breed) or tagNumber is already registered",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content)
    })
    public ResponseEntity<ApiResponse<CowResponse>> create(@Valid @RequestBody CreateCowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cow registered successfully", cowService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all cows (optional status/farm filter)",
            description = "Returns non-deleted cows, page-sorted by tagNumber by default. `status` and `farmId` "
                    + "are independent optional filters that combine with AND when both are supplied; omit either "
                    + "to not filter on it.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Cows retrieved (possibly an empty page if no cows match the filters)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<CowResponse>>> findAll(
            @Parameter(description = "Optional status filter") @RequestParam(required = false) CowStatus status,
            @Parameter(description = "Optional owning-farm filter") @RequestParam(required = false) UUID farmId,
            @Parameter(description = "Standard Spring page/size/sort request parameters")
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "tagNumber") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Cows retrieved successfully", cowService.findAll(status, farmId, pageable)));
    }

    @GetMapping("/ids")
    @Operation(summary = "Get cow ids belonging to a farm (lightweight, for cross-service filter resolution)",
            description = "Returns only the UUIDs of non-deleted cows owned by the given farm, with no paging and "
                    + "no other cow fields. Intended for other services (e.g. reports-service) to resolve a farm "
                    + "filter into a set of cow ids before querying a different service, not for UI listing.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Cow ids retrieved (possibly empty if the farm has no cows)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<UUID>>> findIdsByFarm(
            @Parameter(description = "Farm to resolve cow ids for", required = true) @RequestParam UUID farmId) {
        return ResponseEntity.ok(ApiResponse.success("Cow ids retrieved successfully", cowService.findIdsByFarm(farmId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cow by ID",
            description = "Returns a single cow by id. 404 if the cow does not exist or has been soft-deleted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cow retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<CowResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Cow retrieved successfully", cowService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update cow details",
            description = "Partially updates a cow's profile fields (name, breed, date of birth, purchase date, "
                    + "owning farm). Fields omitted/null in the request are left unchanged - only non-null fields "
                    + "overwrite the existing record (see FarmMapper#updateCowFromRequest, which maps with "
                    + "NullValuePropertyMappingStrategy.IGNORE). tagNumber and status cannot be changed through "
                    + "this endpoint - use PATCH /{id}/status for status changes.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cow updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "A supplied field violates its validation constraint (e.g. cowName/breed too long)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<CowResponse>> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateCowRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cow updated successfully", cowService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update cow status (ACTIVE/SICK/SOLD/DECEASED)",
            description = "Changes a cow's status, enforcing the transition rules in CowStatus#canTransitionTo: "
                    + "from ACTIVE or SICK, the cow may move to any of the four statuses (including toggling "
                    + "between ACTIVE and SICK); SOLD and DECEASED are terminal - once a cow is SOLD or DECEASED "
                    + "its status can never be changed again, not even back to ACTIVE/SICK or between the two "
                    + "terminal statuses. Attempting a transition out of a terminal status is rejected with 400.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cow status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "status is missing, or the cow's current status is terminal (SOLD/DECEASED) and "
                        + "cannot be transitioned",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<CowResponse>> updateStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCowStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cow status updated successfully", cowService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Soft-delete a cow record",
            description = "Marks the cow as deleted (deleted=true) rather than removing the row. Soft-deleted "
                    + "cows are excluded from all other cow/health/vaccination lookups but remain in the database "
                    + "for audit purposes.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cow deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No non-deleted cow exists with the given id", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        cowService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Cow deleted successfully", null));
    }
}
