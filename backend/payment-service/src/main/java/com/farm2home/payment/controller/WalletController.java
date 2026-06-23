package com.farm2home.payment.controller;

import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import com.farm2home.payment.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallet", description = "Customer wallet management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "Get my wallet balance")
    public ResponseEntity<WalletResponse> getMyWallet(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(walletService.getWallet(principal.userId()));
    }

    @PostMapping("/topup")
    @Operation(summary = "Top up wallet balance")
    public ResponseEntity<WalletResponse> topUp(
            @Valid @RequestBody TopUpWalletRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(walletService.topUp(principal.userId(), request));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get wallet transaction history")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(walletService.getTransactions(principal.userId(), pageable));
    }
}
