package com.farm2home.payment.service;

import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    WalletResponse getWallet(UUID customerId);

    WalletResponse topUp(UUID customerId, TopUpWalletRequest request);

    // Called internally by payment flow — not a direct REST endpoint
    void debitForPayment(UUID customerId, BigDecimal amount, UUID paymentId);

    void creditRefund(UUID customerId, BigDecimal amount, UUID paymentId);

    Page<WalletTransactionResponse> getTransactions(UUID customerId, Pageable pageable);
}
