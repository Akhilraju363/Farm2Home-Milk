package com.farm2home.customer.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.domain.enums.ConsentPurpose;
import com.farm2home.customer.dto.request.ConsentBulkUpdateRequest;
import com.farm2home.customer.dto.response.ConsentResponse;
import com.farm2home.customer.service.impl.ConsentServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Self-service DPDP consent capture (self-only - customerId is always taken from the bearer
 *  token, same convention as CustomerController's /me/addresses). See DPDP_PROGRESS.md. */
@RestController
@RequestMapping("/api/v1/customers/me/consents")
@Tag(name = "Consent", description = "Per-purpose DPDP consent records for the caller's own account.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentServiceImpl service;

    @PostMapping
    @Operation(summary = "Record consent choices",
            description = "Upserts one or more per-purpose consent choices for the caller (e.g. the "
                    + "checkboxes shown at registration, submitted together). Each purpose is independent - "
                    + "granting one does not imply another.")
    public ResponseEntity<ApiResponse<List<ConsentResponse>>> upsert(
            @Valid @RequestBody ConsentBulkUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        List<ConsentResponse> result = service.upsert(
                principal.userId(), request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"), "REGISTRATION");
        return ResponseEntity.ok(ApiResponse.success("Consent recorded successfully", result));
    }

    @GetMapping
    @Operation(summary = "Get my current consent choices",
            description = "Returns only purposes that have an explicit recorded choice - a purpose with no "
                    + "row yet has never been asked/answered (frontend treats this the same as not granted).")
    public ResponseEntity<ApiResponse<List<ConsentResponse>>> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Consents retrieved successfully", service.findAll(principal.userId())));
    }

    @PutMapping("/{purpose}")
    @Operation(summary = "Grant or withdraw one purpose",
            description = "Single-purpose toggle for a settings screen - withdrawing consent here is exactly "
                    + "as easy as granting it (DPDP s.6(4)).")
    public ResponseEntity<ApiResponse<ConsentResponse>> setOne(
            @PathVariable ConsentPurpose purpose,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        boolean granted = Boolean.TRUE.equals(body.get("granted"));
        ConsentResponse result = service.setOne(principal.userId(), purpose, granted, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Consent updated successfully", result));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return (forwardedFor != null && !forwardedFor.isBlank()) ? forwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }
}
