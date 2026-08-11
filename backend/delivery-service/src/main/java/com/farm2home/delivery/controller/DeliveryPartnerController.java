package com.farm2home.delivery.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.UpdatePartnerRequest;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.service.impl.DeliveryPartnerServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery/partners")
@Tag(name = "Delivery Partners", description = "Manage delivery partners: registration, listing, lookup, and updates.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryPartnerController {

    private final DeliveryPartnerServiceImpl partnerService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Register a delivery partner (admin)",
            description = "Creates a delivery partner profile linked to an existing auth-service user id. If "
                    + "routeId is provided it must reference an existing, non-deleted route. The new partner is "
                    + "active by default.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Partner created",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Delivery partner created successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "userId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeCode": "NZ-01",
                                    "name": "Ramesh Kumar",
                                    "mobile": "9876543210",
                                    "vehicleType": "Two-wheeler",
                                    "active": true,
                                    "createdAt": "2026-07-31T09:00:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failure (missing userId/name/mobile)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "routeId does not reference an existing, non-deleted route", content = @Content)
    })
    public ResponseEntity<ApiResponse<PartnerResponse>> create(@Valid @RequestBody CreatePartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery partner created successfully", partnerService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "List all delivery partners (admin)",
            description = "Paginated list of all non-deleted delivery partners, sorted by name by default. "
                    + "Not filtered by the active flag - inactive partners are included.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Partners retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<PartnerResponse>>> findAll(
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Delivery partners retrieved successfully", partnerService.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get partner by ID",
            description = "No role restriction beyond authentication - any authenticated caller may look up "
                    + "any non-deleted partner by id.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Partner retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Delivery partner retrieved successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "userId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeCode": "NZ-01",
                                    "name": "Ramesh Kumar",
                                    "mobile": "9876543210",
                                    "vehicleType": "Two-wheeler",
                                    "active": true,
                                    "createdAt": "2026-07-31T09:00:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Partner not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<ApiResponse<PartnerResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Delivery partner retrieved successfully", partnerService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Update partner (admin)",
            description = "Partial update - only non-null fields are applied (see UpdatePartnerRequest); "
                    + "bean-validation constraints (name/mobile length and format) are enforced via @Valid.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Partner updated",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Delivery partner updated successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "userId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "routeCode": "NZ-01",
                                    "name": "Ramesh Kumar",
                                    "mobile": "9876543210",
                                    "vehicleType": "Four-wheeler",
                                    "active": true,
                                    "createdAt": "2026-07-31T09:00:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failure (name/vehicleType too long, mobile not a valid 10-digit "
                        + "Indian number)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Partner not found (or soft-deleted), or routeId does not reference an existing, "
                        + "non-deleted route", content = @Content)
    })
    public ResponseEntity<ApiResponse<PartnerResponse>> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdatePartnerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Delivery partner updated successfully", partnerService.update(id, request)));
    }
}
