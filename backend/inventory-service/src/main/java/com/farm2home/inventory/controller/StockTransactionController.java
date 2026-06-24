package com.farm2home.inventory.controller;

import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
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
@RequiredArgsConstructor
public class StockTransactionController {

    private final StockTransactionServiceImpl service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockTransactionResponse> transact(
            @PathVariable UUID itemId,
            @Valid @RequestBody StockTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transact(itemId, request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<StockTransactionResponse>> history(
            @PathVariable UUID itemId, Pageable pageable) {
        return ResponseEntity.ok(service.findByItem(itemId, pageable));
    }
}
