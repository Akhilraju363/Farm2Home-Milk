package com.farm2home.customer.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.dto.request.CreateDataRightsRequestRequest;
import com.farm2home.customer.dto.request.UpdateDataRightsRequestStatusRequest;
import com.farm2home.customer.dto.response.DataRightsRequestResponse;
import com.farm2home.customer.service.impl.DataRightsRequestServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.UUID;

/** DPDP data-rights/grievance intake. POST is deliberately public (see SecurityConfig's
 *  dataRightsPublicPost matcher) - a data principal must be able to reach this channel whether or
 *  not they have an account. GET/PATCH (triage) are SUPER_ADMIN only - there is no dedicated
 *  "compliance officer" role in this system yet, see DPDP_PROGRESS.md "Open items". */
@RestController
@RequestMapping("/api/v1/data-rights-requests")
@Tag(name = "Data Rights Requests", description = "DPDP access/correction/erasure/withdraw-consent "
        + "requests and general privacy grievances.")
@RequiredArgsConstructor
public class DataRightsRequestController {

    private final DataRightsRequestServiceImpl service;

    @PostMapping("/submit")
    @Operation(summary = "Submit a data-rights request or grievance",
            description = "Public - no login required. If the caller happens to be authenticated, their "
                    + "customerId is recorded alongside the contact details they entered, but is never "
                    + "required.")
    public ResponseEntity<ApiResponse<DataRightsRequestResponse>> create(
            @Valid @RequestBody CreateDataRightsRequestRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID customerId = principal != null ? principal.userId() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Request submitted successfully", service.create(request, customerId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "List data-rights requests (admin)", description = "Most recent first.")
    public ResponseEntity<ApiResponse<Page<DataRightsRequestResponse>>> findAll(
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Requests retrieved successfully", service.findAll(pageable)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Triage a request (admin)", description = "Sets status and optional resolution notes.")
    public ResponseEntity<ApiResponse<DataRightsRequestResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateDataRightsRequestStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Request updated successfully", service.updateStatus(id, request)));
    }
}
