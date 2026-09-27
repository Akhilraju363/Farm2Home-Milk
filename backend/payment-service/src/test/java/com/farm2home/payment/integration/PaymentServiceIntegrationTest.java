package com.farm2home.payment.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
import com.farm2home.payment.client.OrderServiceClient;
import com.farm2home.payment.client.OrderStatusResponse;
import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.entity.Wallet;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.domain.repository.WalletRepository;
import com.farm2home.payment.domain.repository.WalletTransactionRepository;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.TopUpWalletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Payment Service using Testcontainers and PostgreSQL.
 * Tests complete payment workflows including wallet management and payment processing.
 *
 * <p>Only {@link OrderServiceClient} is mocked - initiate() verifies the order against
 * order-service, a separate deployable that doesn't exist in this test's context. The payment
 * gateway is the real default {@code mock} provider (payment.gateway.provider is unset here).
 *
 * <p>The legacy {@code POST /payments/callback} is enabled for this test only: it is disabled by
 * default and documented as the local-dev/test way to simulate gateway outcomes under the mock
 * provider (see PaymentController.callback). It still requires an admin caller.
 */
@DisplayName("Payment Service Integration Tests")
@TestPropertySource(properties = "farm2home.payment.legacy-callback-enabled=true")
class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderServiceClient orderServiceClient;

    private UUID customerId;
    private UUID orderId;
    private AuthenticationTestBuilder authBuilder;
    private AuthenticationTestBuilder adminAuthBuilder;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        authBuilder = new AuthenticationTestBuilder()
                .withUserId(customerId)
                .withMobile("9876543210")
                .withRoles("CUSTOMER");
        adminAuthBuilder = new AuthenticationTestBuilder()
                .withMobile("9876500000")
                .withRoles("FARM_MANAGER");
        paymentRepository.deleteAll();
        // wallet_transactions.wallet_id references wallets(id) with no ON DELETE CASCADE - a
        // top-up leaves transaction rows behind, so they must go before their wallets.
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();

        var order = new OrderStatusResponse();
        order.setId(orderId);
        order.setStatus("PENDING");
        when(orderServiceClient.getOrder(any())).thenReturn(Mono.just(order));
    }

    @Nested
    @DisplayName("Payment Initiation")
    class PaymentInitiationTests {

        @Test
        @DisplayName("Should initiate payment via UPI")
        void shouldInitiatePaymentViaUPI() throws Exception {
            var paymentRequest = new InitiatePaymentRequest();
            paymentRequest.setOrderId(orderId);
            paymentRequest.setAmount(BigDecimal.valueOf(500.00));
            paymentRequest.setPaymentMethod(PaymentMethod.UPI);

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(paymentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id", notNullValue()))
                    .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.PENDING.name()))
                    .andExpect(jsonPath("$.data.paymentMethod").value(PaymentMethod.UPI.name()))
                    .andExpect(jsonPath("$.data.amount").value(500.00));

            var savedPayments = paymentRepository.findAll();
            assertThat(savedPayments).hasSize(1);
            assertThat(savedPayments.get(0).getCustomerId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("Should initiate payment via Card")
        void shouldInitiatePaymentViaCard() throws Exception {
            var paymentRequest = new InitiatePaymentRequest();
            paymentRequest.setOrderId(orderId);
            paymentRequest.setAmount(BigDecimal.valueOf(750.00));
            paymentRequest.setPaymentMethod(PaymentMethod.RAZORPAY);

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(paymentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.paymentMethod").value(PaymentMethod.RAZORPAY.name()));
        }

        @Test
        @DisplayName("Should reject invalid payment amount")
        void shouldRejectInvalidPaymentAmount() throws Exception {
            var paymentRequest = new InitiatePaymentRequest();
            paymentRequest.setOrderId(orderId);
            paymentRequest.setAmount(BigDecimal.valueOf(-100));
            paymentRequest.setPaymentMethod(PaymentMethod.UPI);

            mockMvc.perform(post("/api/v1/payments")
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

            var callbackRequest = new PaymentCallbackRequest();
            callbackRequest.setPaymentReference(payment.getPaymentReference());
            callbackRequest.setSuccess(true);
            callbackRequest.setGatewayResponse("Payment approved");

            mockMvc.perform(post("/api/v1/payments/callback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(callbackRequest))
                    .with(adminAuthBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.SUCCESS.name()));

            var updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updatedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should process failed payment callback")
        void shouldProcessFailedPaymentCallback() throws Exception {
            var payment = createTestPayment(PaymentStatus.PENDING);

            var callbackRequest = new PaymentCallbackRequest();
            callbackRequest.setPaymentReference(payment.getPaymentReference());
            callbackRequest.setSuccess(false);
            callbackRequest.setErrorMessage("Insufficient funds");

            mockMvc.perform(post("/api/v1/payments/callback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(callbackRequest))
                    .with(adminAuthBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.FAILED.name()));
        }

        @Test
        @DisplayName("Should retrieve payment by ID")
        void shouldRetrievePaymentById() throws Exception {
            var payment = createTestPayment(PaymentStatus.SUCCESS);

            mockMvc.perform(get("/api/v1/payments/" + payment.getId())
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(payment.getId().toString()))
                    .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.SUCCESS.name()));
        }
    }

    @Nested
    @DisplayName("Wallet Management")
    class WalletManagementTests {

        @Test
        @DisplayName("Should create wallet for new customer on first access")
        void shouldCreateWalletForNewCustomer() throws Exception {
            // There is no explicit create endpoint - GET /wallets/me creates a zero-balance
            // wallet on first access (see WalletController.getMyWallet).
            mockMvc.perform(get("/api/v1/wallets/me")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.data.balance").value(0.0));

            var wallets = walletRepository.findByCustomerId(customerId);
            assertThat(wallets).isNotEmpty();
        }

        @Test
        @DisplayName("Should top up wallet")
        void shouldTopUpWallet() throws Exception {
            createTestWallet(BigDecimal.ZERO);

            var topUpRequest = new TopUpWalletRequest();
            topUpRequest.setAmount(BigDecimal.valueOf(1000.00));
            topUpRequest.setDescription("Top-up via UPI");

            mockMvc.perform(post("/api/v1/wallets/topup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(topUpRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.balance").value(1000.00));

            var wallet = walletRepository.findByCustomerId(customerId).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        }

        @Test
        @DisplayName("Should retrieve wallet details")
        void shouldRetrieveWalletDetails() throws Exception {
            var wallet = createTestWallet(BigDecimal.valueOf(500.00));

            mockMvc.perform(get("/api/v1/wallets/me")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.data.balance").value(500.00));
        }

        @Test
        @DisplayName("Should not allow negative balance")
        void shouldNotAllowNegativeBalance() throws Exception {
            createTestWallet(BigDecimal.valueOf(100.00));

            var topUpRequest = new TopUpWalletRequest();
            topUpRequest.setAmount(BigDecimal.valueOf(-500.00));
            topUpRequest.setDescription("Negative top-up");

            mockMvc.perform(post("/api/v1/wallets/topup")
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
                .paymentStatus(status)
                .paymentMethod(PaymentMethod.UPI)
                .paymentReference("REF-" + UUID.randomUUID())
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
