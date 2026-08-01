package com.farm2home.dashboard.controller;

import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.dashboard.dto.response.DashboardSummaryResponse;
import com.farm2home.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention used across the platform's other controllers.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregated cross-service metrics for the admin/ops dashboard")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '"
            + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get the aggregated dashboard summary",
            description = "Total customers, active subscriptions, today's/pending orders, "
                    + "completed deliveries today, today's/this month's revenue, low stock products, "
                    + "today's milk production, and recent notifications - fetched in parallel from "
                    + "each owning service so the frontend makes exactly one call.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Summary retrieved. Any individual downstream service that times out (default "
                        + "3s, dashboard.downstream-timeout-ms) or errors degrades its own field to a safe "
                        + "fallback value (e.g. zero counts, empty lists) rather than failing the whole response "
                        + "- see DashboardServiceImpl#withFallback."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER authority", content = @Content)
    })
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
            @Parameter(description = "Max low-stock products to embed") @RequestParam(defaultValue = "5") int lowStockLimit,
            @Parameter(description = "Max recent notifications to embed") @RequestParam(defaultValue = "10") int recentNotificationsLimit) {
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary retrieved successfully",
                dashboardService.getSummary(lowStockLimit, recentNotificationsLimit)));
    }
}
