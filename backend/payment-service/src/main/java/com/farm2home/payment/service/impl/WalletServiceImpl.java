package com.farm2home.payment.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.payment.domain.entity.Wallet;
import com.farm2home.payment.domain.entity.WalletTransaction;
import com.farm2home.payment.domain.enums.TransactionType;
import com.farm2home.payment.domain.repository.WalletRepository;
import com.farm2home.payment.domain.repository.WalletTransactionRepository;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import com.farm2home.payment.exception.InsufficientBalanceException;
import com.farm2home.payment.mapper.PaymentMapper;
import com.farm2home.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txRepository;
    private final PaymentMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID customerId) {
        return mapper.toWalletResponse(findOrCreateWallet(customerId));
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.PAYMENT, entityType = "Wallet")
    public WalletResponse topUp(UUID customerId, TopUpWalletRequest request) {
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().customerId(customerId).build()));

        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        txRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(TransactionType.CREDIT)
                .amount(request.getAmount())
                .description(request.getDescription() != null ? request.getDescription() : "Wallet top-up")
                .build());

        return mapper.toWalletResponse(wallet);
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.PAYMENT, entityType = "Wallet")
    public void debitForPayment(UUID customerId, BigDecimal amount, UUID paymentId) {
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new InsufficientBalanceException("Wallet not found for customer"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient wallet balance. Available: ₹" + wallet.getBalance() + ", Required: ₹" + amount);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        txRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .referenceId(paymentId)
                .description("Payment for order")
                .build());
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.PAYMENT, entityType = "Wallet")
    public void creditRefund(UUID customerId, BigDecimal amount, UUID paymentId) {
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().customerId(customerId).build()));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        txRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(TransactionType.CREDIT)
                .amount(amount)
                .referenceId(paymentId)
                .description("Refund credited to wallet")
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(UUID customerId, Pageable pageable) {
        Wallet wallet = findOrCreateWallet(customerId);
        return txRepository.findAllByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(mapper::toTransactionResponse);
    }

    private Wallet findOrCreateWallet(UUID customerId) {
        return walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().customerId(customerId).build()));
    }
}
