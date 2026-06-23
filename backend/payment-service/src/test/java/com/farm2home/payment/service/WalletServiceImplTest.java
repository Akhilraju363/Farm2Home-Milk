package com.farm2home.payment.service;

import com.farm2home.payment.domain.entity.Wallet;
import com.farm2home.payment.domain.entity.WalletTransaction;
import com.farm2home.payment.domain.enums.TransactionType;
import com.farm2home.payment.domain.repository.WalletRepository;
import com.farm2home.payment.domain.repository.WalletTransactionRepository;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.exception.InsufficientBalanceException;
import com.farm2home.payment.mapper.PaymentMapper;
import com.farm2home.payment.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository txRepository;
    @Mock private PaymentMapper mapper;

    @InjectMocks private WalletServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID walletId   = UUID.randomUUID();

    private Wallet buildWallet(BigDecimal balance) {
        return Wallet.builder().id(walletId).customerId(customerId).balance(balance).build();
    }

    // ── TopUp ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("topUp()")
    class TopUp {

        @Test
        @DisplayName("existing wallet → increases balance, records CREDIT transaction")
        void topUpExistingWallet() {
            Wallet wallet = buildWallet(new BigDecimal("100.00"));
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(wallet)).thenReturn(wallet);
            when(mapper.toWalletResponse(wallet)).thenReturn(
                    WalletResponse.builder().balance(new BigDecimal("350.00")).build());

            TopUpWalletRequest req = new TopUpWalletRequest();
            req.setAmount(new BigDecimal("250.00"));
            req.setDescription("Test top-up");

            service.topUp(customerId, req);

            assertThat(wallet.getBalance()).isEqualByComparingTo("350.00");
            verify(txRepository).save(argThat(tx ->
                    tx.getTransactionType() == TransactionType.CREDIT &&
                    tx.getAmount().compareTo(new BigDecimal("250.00")) == 0));
        }

        @Test
        @DisplayName("no existing wallet → auto-creates wallet then tops up")
        void topUpNewWallet() {
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());
            Wallet newWallet = buildWallet(BigDecimal.ZERO);
            when(walletRepository.save(any())).thenReturn(newWallet);
            when(mapper.toWalletResponse(any())).thenReturn(
                    WalletResponse.builder().balance(new BigDecimal("500.00")).build());

            TopUpWalletRequest req = new TopUpWalletRequest();
            req.setAmount(new BigDecimal("500.00"));

            service.topUp(customerId, req);

            // Called twice: once to create, once to update balance
            verify(walletRepository, times(2)).save(any());
        }
    }

    // ── Debit ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("debitForPayment()")
    class Debit {

        @Test
        @DisplayName("sufficient balance → deducts amount, records DEBIT transaction")
        void sufficientBalance() {
            Wallet wallet = buildWallet(new BigDecimal("500.00"));
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(wallet)).thenReturn(wallet);

            service.debitForPayment(customerId, new BigDecimal("150.00"), UUID.randomUUID());

            assertThat(wallet.getBalance()).isEqualByComparingTo("350.00");
            verify(txRepository).save(argThat(tx ->
                    tx.getTransactionType() == TransactionType.DEBIT &&
                    tx.getAmount().compareTo(new BigDecimal("150.00")) == 0));
        }

        @Test
        @DisplayName("insufficient balance → throws InsufficientBalanceException")
        void insufficientBalance() {
            Wallet wallet = buildWallet(new BigDecimal("50.00"));
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> service.debitForPayment(customerId, new BigDecimal("150.00"), null))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient wallet balance");

            verify(walletRepository, never()).save(any());
            verify(txRepository, never()).save(any());
        }

        @Test
        @DisplayName("exact balance → deducts to zero")
        void exactBalance() {
            Wallet wallet = buildWallet(new BigDecimal("150.00"));
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(wallet)).thenReturn(wallet);

            service.debitForPayment(customerId, new BigDecimal("150.00"), null);

            assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("wallet not found → throws InsufficientBalanceException")
        void noWallet_throws() {
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.debitForPayment(customerId, new BigDecimal("100.00"), null))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Wallet not found");
        }
    }

    // ── CreditRefund ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("creditRefund()")
    class CreditRefund {

        @Test
        @DisplayName("credits amount back to wallet and records CREDIT transaction")
        void creditsRefund() {
            Wallet wallet = buildWallet(new BigDecimal("100.00"));
            UUID refPaymentId = UUID.randomUUID();
            when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(wallet)).thenReturn(wallet);

            service.creditRefund(customerId, new BigDecimal("200.00"), refPaymentId);

            assertThat(wallet.getBalance()).isEqualByComparingTo("300.00");
            verify(txRepository).save(argThat(tx ->
                    tx.getTransactionType() == TransactionType.CREDIT &&
                    tx.getReferenceId().equals(refPaymentId) &&
                    tx.getDescription().contains("Refund")));
        }
    }
}
