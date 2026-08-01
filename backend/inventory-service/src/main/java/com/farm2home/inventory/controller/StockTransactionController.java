package com.farm2home.inventory.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/{itemId}/transactions")
@Tag(name = "Stock Transactions", description = "IN/OUT stock movements against a single inventory item. This is "
        + "the only way an inventory item's quantity ever changes - creating or updating the item itself "
        + "(see Inventory Items) never touches its quantity.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class StockTransactionController {

    private final StockTransactionServiceImpl service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Record a stock transaction",
            description = "Applies an IN (add) or OUT (remove) quantity movement to the given inventory item and "
                    + "atomically updates the item's stored quantity in the same transaction. An OUT transaction "
                    + "that would drive the item's quantity below zero is rejected outright - negative inventory "
                    + "is never allowed (see StockTransactionServiceImpl.transact, which compares the requested "
                    + "OUT quantity against the item's current quantity before applying it).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Transaction recorded and item quantity updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (missing txnType/quantity, quantity not greater than zero), OR "
                        + "an OUT transaction was requested for more than the item's current available quantity "
                        + "(\"Insufficient stock\" - both cases return 400, not 409/422, since InventoryException "
                        + "is mapped to HTTP 400 by GlobalExceptionHandler)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No inventory item with this itemId (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<StockTransactionResponse>> transact(
            @PathVariable UUID itemId,
            @Valid @RequestBody StockTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock transaction recorded successfully", service.transact(itemId, request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get stock transaction history",
            description = "Paginated IN/OUT transaction history for a single inventory item, most recent first.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Transaction history retrieved (possibly empty if the item has never moved)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No inventory item with this itemId (or it has been deleted)", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<StockTransactionResponse>>> history(
            @PathVariable UUID itemId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Stock transaction history retrieved successfully", service.findByItem(itemId, pageable)));
    }
}
