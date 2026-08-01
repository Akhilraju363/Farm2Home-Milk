package com.farm2home.payment.controller;

import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import com.farm2home.payment.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here is scoped to the authenticated caller's own wallet (resolved from
 * {@code principal.userId()}, never a path/query parameter) - there is no admin "view any
 * customer's wallet" endpoint, and no role beyond "authenticated" is required, so the
 * per-endpoint security requirement is uniformly just the class-level bearer JWT.
 *
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention OrderController/SubscriptionController already use.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallet", description = "Customer in-app wallet: balance, top-ups, and transaction history. "
        + "Every endpoint operates on the authenticated caller's own wallet.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "Get my wallet balance",
            description = "Returns the authenticated customer's wallet, creating one with a zero balance on "
                    + "first access if none exists yet (see WalletServiceImpl.findOrCreateWallet - a wallet is "
                    + "never explicitly \"created\" by a separate endpoint).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Wallet retrieved (auto-created if this is the first call)",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Wallet balance retrieved successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "customerId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "balance": 1250.00,
                                    "updatedAt": "2026-07-31T10:15:30"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Wallet balance retrieved successfully", walletService.getWallet(principal.userId())));
    }

    @PostMapping("/topup")
    @Operation(summary = "Top up wallet balance",
            description = "Credits the authenticated customer's own wallet by `amount` and records a CREDIT "
                    + "wallet transaction. This is a direct top-up (e.g. after an out-of-band payment) - it does "
                    + "not itself charge any payment method.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Wallet credited; returns the new balance",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Wallet topped up successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "customerId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "balance": 1750.00,
                                    "updatedAt": "2026-07-31T10:16:05"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "amount missing, below the 1.00 minimum, or description exceeds 255 characters",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<WalletResponse>> topUp(
            @Valid @RequestBody TopUpWalletRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Wallet topped up successfully", walletService.topUp(principal.userId(), request)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get wallet transaction history",
            description = "Paginated CREDIT/DEBIT transaction history for the authenticated customer's own "
                    + "wallet, most recent first.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Transactions retrieved (possibly empty if the wallet has never moved)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Standard Spring page/size/sort request parameters")
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Wallet transactions retrieved successfully", walletService.getTransactions(principal.userId(), pageable)));
    }
}
