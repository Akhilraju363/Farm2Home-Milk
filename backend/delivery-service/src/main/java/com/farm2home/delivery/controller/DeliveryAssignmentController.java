package com.farm2home.delivery.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.service.DeliveryAssignmentService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery/assignments")
@Tag(name = "Delivery Assignments", description = "Delivery assignment tracking")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Manually assign an order to a delivery partner (admin)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> manualAssign(
            @Valid @RequestBody ManualAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assignment created successfully", assignmentService.manualAssign(request)));
    }

    @GetMapping
    @Operation(summary = "List assignments (admin sees all, partner sees own)")
    public ResponseEntity<ApiResponse<Page<AssignmentResponse>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "assignedAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Assignments retrieved successfully", assignmentService.findAll(
                principal.userId(), principal.isAdmin(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get assignment by ID")
    public ResponseEntity<ApiResponse<AssignmentResponse>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Assignment retrieved successfully", assignmentService.findById(id, principal.userId(), principal.isAdmin())));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get all assignments for an order")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> findByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order assignments retrieved successfully", assignmentService.findByOrderId(orderId)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update delivery assignment status (partner or admin)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssignmentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Assignment status updated successfully", assignmentService.updateStatus(
                id, request, principal.userId(), principal.isAdmin())));
    }
}
