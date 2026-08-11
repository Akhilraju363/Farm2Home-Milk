package com.farm2home.customer.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.customer.dto.response.LocationCityResponse;
import com.farm2home.customer.dto.response.LocationDistrictResponse;
import com.farm2home.customer.dto.response.LocationStateResponse;
import com.farm2home.customer.service.impl.LocationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only India location reference data for cascading State -> District -> City dropdowns
 *  (currently used by the registration Address step). Reachable by any authenticated caller, same
 *  convention as other non-ownership-scoped reference-data GETs in this codebase (e.g.
 *  FarmController's GET /farm) - no role restriction, and no write endpoints exist here at all. */
@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Locations", description = "India state/district/city reference data")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LocationController {

    private final LocationServiceImpl locationService;

    @GetMapping("/states")
    @Operation(summary = "List all states and union territories", description = "Every active state/UT, "
            + "alphabetically ordered. Source of truth for the Address step's State dropdown.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "States retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<LocationStateResponse>>> getStates() {
        return ResponseEntity.ok(ApiResponse.success("States retrieved successfully", locationService.getStates()));
    }

    @GetMapping("/states/{stateId}/districts")
    @Operation(summary = "List districts for a state", description = "Every active district belonging to the "
            + "given state, alphabetically ordered. Never returns districts belonging to a different state.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Districts retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No active state with this id", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<LocationDistrictResponse>>> getDistricts(@PathVariable UUID stateId) {
        return ResponseEntity.ok(ApiResponse.success("Districts retrieved successfully", locationService.getDistricts(stateId)));
    }

    @GetMapping("/districts/{districtId}/cities")
    @Operation(summary = "List cities/towns for a district", description = "Every active city/town belonging to "
            + "the given district, alphabetically ordered. Never returns cities belonging to a different district.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cities retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No active district with this id", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<LocationCityResponse>>> getCities(@PathVariable UUID districtId) {
        return ResponseEntity.ok(ApiResponse.success("Cities retrieved successfully", locationService.getCities(districtId)));
    }
}
