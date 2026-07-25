package com.farm2home.payment.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.service.PaymentService;
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
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing endpoints")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate a payment for an order")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated successfully", paymentService.initiate(request, principal.userId())));
    }

    @PostMapping("/callback")
    @Operation(summary = "Payment gateway webhook — updates payment status")
    public ResponseEntity<ApiResponse<PaymentResponse>> callback(
            @Valid @RequestBody PaymentCallbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment status updated successfully", paymentService.processCallback(request)));
    }

    @GetMapping
    @Operation(summary = "List payments (admin sees all, customer sees own)")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", paymentService.findAll(
                principal.userId(), principal.isAdmin(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", paymentService.findById(id, principal.userId(), principal.isAdmin())));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get all payments for an order")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> findByOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Order payments retrieved successfully", paymentService.findByOrderId(
                orderId, principal.userId(), principal.isAdmin())));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('FARM_MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Refund a payment (admin only)")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Payment refunded successfully", paymentService.refund(id, principal.userId(), principal.isAdmin())));
    }
}
