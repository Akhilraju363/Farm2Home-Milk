package com.farm2home.payment.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.entity.Wallet;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.domain.repository.WalletRepository;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Payment Service using Testcontainers and PostgreSQL.
 * Tests complete payment workflows including wallet management and payment processing.
 */
@DisplayName("Payment Service Integration Tests")
class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID orderId;
    private AuthenticationTestBuilder authBuilder;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        authBuilder = new AuthenticationTestBuilder()
                .withUserId(customerId)
                .withUsername("customer@farm2home.com")
                .withRoles("CUSTOMER");
        paymentRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Nested
    @DisplayName("Payment Initiation")
    class PaymentInitiationTests {

        @Test
        @DisplayName("Should initiate payment via UPI")
        void shouldInitiatePaymentViaUPI() throws Exception {
            var paymentRequest = InitiatePaymentRequest.builder()
                    .orderId(orderId)
                    .amount(BigDecimal.valueOf(500.00))
                    .paymentMethod(PaymentMethod.UPI)
                    .upiId("customer@upi")
                    .build();

            mockMvc.perform(post("/api/v1/payments/initiate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(paymentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                    .andExpect(jsonPath("$.paymentMethod").value(PaymentMethod.UPI.name()))
                    .andExpect(jsonPath("$.amount").value(500.00));

            var savedPayments = paymentRepository.findAll();
            assertThat(savedPayments).hasSize(1);
            assertThat(savedPayments.get(0).getCustomerId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("Should initiate payment via Card")
        void shouldInitiatePaymentViaCard() throws Exception {
            var paymentRequest = InitiatePaymentRequest.builder()
                    .orderId(orderId)
                    .amount(BigDecimal.valueOf(750.00))
                    .paymentMethod(PaymentMethod.CARD)
                    .cardTokenId("card_token_123")
                    .build();

            mockMvc.perform(post("/api/v1/payments/initiate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(paymentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paymentMethod").value(PaymentMethod.CARD.name()));
        }

        @Test
        @DisplayName("Should reject invalid payment amount")
        void shouldRejectInvalidPaymentAmount() throws Exception {
            var paymentRequest = InitiatePaymentRequest.builder()
                    .orderId(orderId)
                    .amount(BigDecimal.valueOf(-100))
                    .paymentMethod(PaymentMethod.UPI)
                    .upiId("customer@upi")
                    .build();

            mockMvc.perform(post("/api/v1/payments/initiate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(paymentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Payment Processing")
    class PaymentProcessingTests {

        @Test
        @DisplayName("Should process successful payment callback")
        void shouldProcessSuccessfulPaymentCallback() throws Exception {
            var payment = createTestPayment(PaymentStatus.PENDING);

            var callbackRequest = PaymentCallbackRequest.builder()
                    .paymentId(payment.getId())
                    .status(PaymentStatus.COMPLETED)
                    .referenceId("TXN-12345")
                    .build();

            mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/callback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(callbackRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(PaymentStatus.COMPLETED.name()));

            var updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should process failed payment callback")
        void shouldProcessFailedPaymentCallback() throws Exception {
            var payment = createTestPayment(PaymentStatus.PENDING);

            var callbackRequest = PaymentCallbackRequest.builder()
                    .paymentId(payment.getId())
                    .status(PaymentStatus.FAILED)
                    .failureReason("Insufficient funds")
                    .build();

            mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/callback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(callbackRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(PaymentStatus.FAILED.name()));
        }

        @Test
        @DisplayName("Should retrieve payment by ID")
        void shouldRetrievePaymentById() throws Exception {
            var payment = createTestPayment(PaymentStatus.COMPLETED);

            mockMvc.perform(get("/api/v1/payments/" + payment.getId())
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                    .andExpect(jsonPath("$.status").value(PaymentStatus.COMPLETED.name()));
        }
    }

    @Nested
    @DisplayName("Wallet Management")
    class WalletManagementTests {

        @Test
        @DisplayName("Should create wallet for new customer")
        void shouldCreateWalletForNewCustomer() throws Exception {
            mockMvc.perform(post("/api/v1/wallet")
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.balance").value(0.0));

            var wallets = walletRepository.findByCustomerId(customerId);
            assertThat(wallets).isNotEmpty();
        }

        @Test
        @DisplayName("Should top up wallet")
        void shouldTopUpWallet() throws Exception {
            createTestWallet(BigDecimal.ZERO);

            var topUpRequest = TopUpWalletRequest.builder()
                    .amount(BigDecimal.valueOf(1000.00))
                    .paymentMethod(PaymentMethod.CARD)
                    .cardTokenId("card_token_123")
                    .build();

            mockMvc.perform(post("/api/v1/wallet/topup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(topUpRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(1000.00));

            var wallet = walletRepository.findByCustomerId(customerId).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        }

        @Test
        @DisplayName("Should retrieve wallet details")
        void shouldRetrieveWalletDetails() throws Exception {
            var wallet = createTestWallet(BigDecimal.valueOf(500.00));

            mockMvc.perform(get("/api/v1/wallet")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.balance").value(500.00));
        }

        @Test
        @DisplayName("Should not allow negative balance")
        void shouldNotAllowNegativeBalance() throws Exception {
            createTestWallet(BigDecimal.valueOf(100.00));

            var topUpRequest = TopUpWalletRequest.builder()
                    .amount(BigDecimal.valueOf(-500.00))
                    .paymentMethod(PaymentMethod.CARD)
                    .cardTokenId("card_token_123")
                    .build();

            mockMvc.perform(post("/api/v1/wallet/topup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(topUpRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isBadRequest());
        }
    }

    // Helper methods
    private Payment createTestPayment(PaymentStatus status) {
        var payment = Payment.builder()
                .customerId(customerId)
                .orderId(orderId)
                .amount(BigDecimal.valueOf(500.00))
                .status(status)
                .paymentMethod(PaymentMethod.UPI)
                .paymentReference("REF-" + System.currentTimeMillis())
                .build();
        return paymentRepository.save(payment);
    }

    private Wallet createTestWallet(BigDecimal balance) {
        var wallet = Wallet.builder()
                .customerId(customerId)
                .balance(balance)
                .build();
        return walletRepository.save(wallet);
    }
}
