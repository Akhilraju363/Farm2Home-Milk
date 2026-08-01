package com.farm2home.payment.service;

import com.farm2home.payment.client.OrderServiceClient;
import com.farm2home.payment.client.OrderStatusResponse;
import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.VerifyPaymentRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.event.PaymentStatusChangedEvent;
import com.farm2home.payment.exception.PaymentException;
import com.farm2home.payment.exception.ResourceNotFoundException;
import com.farm2home.payment.gateway.GatewayOrder;
import com.farm2home.payment.gateway.GatewayPaymentState;
import com.farm2home.payment.gateway.GatewayPaymentStatus;
import com.farm2home.payment.gateway.GatewayPaymentVerification;
import com.farm2home.payment.gateway.GatewayRefund;
import com.farm2home.payment.gateway.GatewayWebhookEvent;
import com.farm2home.payment.gateway.PaymentGatewayProvider;
import com.farm2home.payment.mapper.PaymentMapper;
import com.farm2home.payment.service.impl.PaymentServiceImpl;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.PaymentAnalyticsPoint;
import com.farm2home.common.core.analytics.RevenueTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.export.ExportFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private WalletService walletService;
    @Mock private PaymentMapper mapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private EntityManager entityManager;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private PaymentGatewayProvider gatewayProvider;

    @InjectMocks private PaymentServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId    = UUID.randomUUID();
    private final UUID paymentId  = UUID.randomUUID();

    private Payment buildPayment(PaymentStatus status, PaymentMethod method) {
        return Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .customerId(customerId)
                .paymentReference("PAY-TEST-ABCD1234")
                .amount(new BigDecimal("150.00"))
                .paymentMethod(method)
                .paymentStatus(status)
                .build();
    }

    private PaymentResponse buildResponse(PaymentStatus status) {
        return PaymentResponse.builder()
                .id(paymentId)
                .paymentStatus(status.name())
                .amount(new BigDecimal("150.00"))
                .build();
    }

    /** Most initiate() tests need order-service to report a payable (non-cancelled) order;
     *  only the order-status tests themselves stub something different. */
    private void stubPayableOrder(String status) {
        OrderStatusResponse order = new OrderStatusResponse();
        order.setId(orderId);
        order.setStatus(status);
        when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(order));
    }

    private void stubGatewayOrder() {
        when(gatewayProvider.createOrder(any())).thenReturn(
                new GatewayOrder("gw_order_1", "gw_checkout_key", new BigDecimal("150.00"), "INR"));
    }

    /** Matches the event PaymentServiceImpl publishes for a given payment/eventType — the actual
     *  Kafka send happens later, off-thread, in PaymentEventListener (tested separately), so unit
     *  tests here only assert that the right Spring event was raised. */
    private static PaymentStatusChangedEvent statusEvent(Payment payment, String eventType) {
        return argThat(e -> e instanceof PaymentStatusChangedEvent pe
                && pe.getPayment() == payment && pe.getEventType().equals(eventType));
    }

    // ── Initiate ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiate()")
    class Initiate {

        @Test
        @DisplayName("UPI payment → creates a gateway order, stays PENDING, no wallet debit")
        void upiPayment_createsPendingRecord() {
            stubPayableOrder("PENDING");
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(false);
            when(mapper.toEntity(any(InitiatePaymentRequest.class))).thenReturn(new Payment());
            stubGatewayOrder();
            Payment saved = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            when(paymentRepository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(buildResponse(PaymentStatus.PENDING));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            PaymentResponse result = service.initiate(req, customerId);

            assertThat(result.getPaymentStatus()).isEqualTo("PENDING");
            assertThat(result.getGatewayCheckoutKeyId()).isEqualTo("gw_checkout_key");
            verifyNoInteractions(walletService);
            verify(paymentRepository).save(argThat(p -> "gw_order_1".equals(p.getGatewayOrderId())));
        }

        @Test
        @DisplayName("WALLET payment → debits wallet, marks SUCCESS immediately, no gateway call")
        void walletPayment_debitsThenSuccess() {
            stubPayableOrder("PENDING");
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(false);
            when(mapper.toEntity(any(InitiatePaymentRequest.class))).thenReturn(new Payment());
            Payment saved = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.WALLET);
            when(paymentRepository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(buildResponse(PaymentStatus.SUCCESS));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.WALLET);

            PaymentResponse result = service.initiate(req, customerId);

            assertThat(result.getPaymentStatus()).isEqualTo("SUCCESS");
            verify(walletService).debitForPayment(eq(customerId), eq(new BigDecimal("150.00")), any());
            verify(eventPublisher).publishEvent(statusEvent(saved, "PAYMENT_SUCCESS"));
            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("CASH payment → stays PENDING, no gateway call")
        void cashPayment_noGatewayCall() {
            stubPayableOrder("PENDING");
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(false);
            when(mapper.toEntity(any(InitiatePaymentRequest.class))).thenReturn(new Payment());
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(mapper.toResponse(any())).thenReturn(buildResponse(PaymentStatus.PENDING));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.CASH);

            service.initiate(req, customerId);

            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("order already has a SUCCESS or PENDING payment → throws PaymentException")
        void duplicatePayment_throws() {
            stubPayableOrder("PENDING");
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(true);

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            assertThatThrownBy(() -> service.initiate(req, customerId))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("already in progress or completed");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("order is CANCELLED → throws PaymentException, never checks for duplicate payments")
        void cancelledOrder_throws() {
            stubPayableOrder("CANCELLED");

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            assertThatThrownBy(() -> service.initiate(req, customerId))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("cancelled order");
            verify(paymentRepository, never()).existsByOrderIdAndPaymentStatusInAndDeletedFalse(any(), any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("order does not exist → throws ResourceNotFoundException")
        void orderNotFound_throws() {
            when(orderServiceClient.getOrder(orderId))
                    .thenReturn(Mono.error(WebClientResponseException.create(404, "Not Found", null, null, null)));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            assertThatThrownBy(() -> service.initiate(req, customerId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("order-service unreachable → throws PaymentException rather than a raw error")
        void orderServiceUnreachable_throwsPaymentException() {
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.error(
                    new org.springframework.web.reactive.function.client.WebClientRequestException(
                            new java.net.ConnectException("connection refused"),
                            org.springframework.http.HttpMethod.GET,
                            java.net.URI.create("http://order-service/api/v1/orders/" + orderId),
                            new org.springframework.http.HttpHeaders())));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            assertThatThrownBy(() -> service.initiate(req, customerId))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Could not verify order status");
        }

        @Test
        @DisplayName("admin sets explicit customerId → uses it instead of caller")
        void adminSetsCustomerId() {
            UUID targetCustomer = UUID.randomUUID();
            stubPayableOrder("PENDING");
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(false);
            when(mapper.toEntity(any(InitiatePaymentRequest.class))).thenReturn(new Payment());
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID()); // real repository.save() always assigns an id
                return p;
            });
            when(mapper.toResponse(any())).thenReturn(buildResponse(PaymentStatus.PENDING));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setCustomerId(targetCustomer);
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.CASH);

            service.initiate(req, customerId);

            verify(paymentRepository).save(argThat(p -> p.getCustomerId().equals(targetCustomer)));
        }
    }

    // ── Verify ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verify()")
    class Verify {

        private Payment pendingWithGatewayOrder() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            payment.setGatewayOrderId("gw_order_1");
            return payment;
        }

        @Test
        @DisplayName("gateway confirms valid + captured → marks SUCCESS")
        void validAndCaptured_marksSuccess() {
            Payment payment = pendingWithGatewayOrder();
            when(paymentRepository.findByIdAndCustomerIdAndDeletedFalse(paymentId, customerId))
                    .thenReturn(Optional.of(payment));
            when(gatewayProvider.verifyPayment(any())).thenReturn(
                    new GatewayPaymentVerification(true, "gw_pay_1", GatewayPaymentState.CAPTURED, "{}"));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.SUCCESS));

            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setGatewayOrderId("gw_order_1");
            req.setGatewayPaymentId("gw_pay_1");
            req.setSignature("sig");

            PaymentResponse result = service.verify(paymentId, req, customerId, false);

            assertThat(result.getPaymentStatus()).isEqualTo("SUCCESS");
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaidAt()).isNotNull();
            assertThat(payment.getGatewayPaymentId()).isEqualTo("gw_pay_1");
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_SUCCESS"));
        }

        @Test
        @DisplayName("gateway reports invalid → marks FAILED, does not throw")
        void invalid_marksFailed() {
            Payment payment = pendingWithGatewayOrder();
            when(paymentRepository.findByIdAndCustomerIdAndDeletedFalse(paymentId, customerId))
                    .thenReturn(Optional.of(payment));
            when(gatewayProvider.verifyPayment(any())).thenReturn(
                    new GatewayPaymentVerification(false, "gw_pay_1", GatewayPaymentState.FAILED, "{}"));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.FAILED));

            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setGatewayOrderId("gw_order_1");
            req.setGatewayPaymentId("gw_pay_1");
            req.setSignature("bad-sig");

            PaymentResponse result = service.verify(paymentId, req, customerId, false);

            assertThat(result.getPaymentStatus()).isEqualTo("FAILED");
            assertThat(payment.getPaidAt()).isNull();
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_FAILED"));
        }

        @Test
        @DisplayName("payment not PENDING → throws PaymentException")
        void notPending_throws() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            when(paymentRepository.findByIdAndCustomerIdAndDeletedFalse(paymentId, customerId))
                    .thenReturn(Optional.of(payment));

            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setGatewayOrderId("gw_order_1");
            req.setGatewayPaymentId("gw_pay_1");
            req.setSignature("sig");

            assertThatThrownBy(() -> service.verify(paymentId, req, customerId, false))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("not in PENDING state");
            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("gateway order id mismatch → throws PaymentException")
        void gatewayOrderMismatch_throws() {
            Payment payment = pendingWithGatewayOrder();
            when(paymentRepository.findByIdAndCustomerIdAndDeletedFalse(paymentId, customerId))
                    .thenReturn(Optional.of(payment));

            VerifyPaymentRequest req = new VerifyPaymentRequest();
            req.setGatewayOrderId("some_other_order");
            req.setGatewayPaymentId("gw_pay_1");
            req.setSignature("sig");

            assertThatThrownBy(() -> service.verify(paymentId, req, customerId, false))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("does not match");
            verifyNoInteractions(gatewayProvider);
        }
    }

    // ── HandleWebhook ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("CAPTURED event for a known PENDING payment → marks SUCCESS")
        void captured_marksSuccess() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.RAZORPAY);
            payment.setGatewayOrderId("gw_order_1");
            when(gatewayProvider.parseWebhookEvent("raw", "sig")).thenReturn(
                    new GatewayWebhookEvent("payment.captured", "gw_order_1", "gw_pay_1", GatewayPaymentState.CAPTURED, "raw"));
            when(paymentRepository.findByGatewayOrderIdAndDeletedFalse("gw_order_1")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);

            service.handleWebhook("raw", "sig");

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getGatewayPaymentId()).isEqualTo("gw_pay_1");
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_SUCCESS"));
        }

        @Test
        @DisplayName("FAILED event for a known PENDING payment → marks FAILED")
        void failed_marksFailed() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.RAZORPAY);
            payment.setGatewayOrderId("gw_order_1");
            when(gatewayProvider.parseWebhookEvent("raw", "sig")).thenReturn(
                    new GatewayWebhookEvent("payment.failed", "gw_order_1", "gw_pay_1", GatewayPaymentState.FAILED, "raw"));
            when(paymentRepository.findByGatewayOrderIdAndDeletedFalse("gw_order_1")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);

            service.handleWebhook("raw", "sig");

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_FAILED"));
        }

        @Test
        @DisplayName("payment already SUCCESS → idempotently ignored, no save")
        void alreadyTerminal_ignored() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.RAZORPAY);
            payment.setGatewayOrderId("gw_order_1");
            when(gatewayProvider.parseWebhookEvent("raw", "sig")).thenReturn(
                    new GatewayWebhookEvent("payment.captured", "gw_order_1", "gw_pay_1", GatewayPaymentState.CAPTURED, "raw"));
            when(paymentRepository.findByGatewayOrderIdAndDeletedFalse("gw_order_1")).thenReturn(Optional.of(payment));

            service.handleWebhook("raw", "sig");

            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("unknown gateway order id → ignored, no exception")
        void unknownOrder_ignored() {
            when(gatewayProvider.parseWebhookEvent("raw", "sig")).thenReturn(
                    new GatewayWebhookEvent("payment.captured", "gw_order_unknown", "gw_pay_1", GatewayPaymentState.CAPTURED, "raw"));
            when(paymentRepository.findByGatewayOrderIdAndDeletedFalse("gw_order_unknown")).thenReturn(Optional.empty());

            service.handleWebhook("raw", "sig");

            verify(paymentRepository, never()).save(any());
        }
    }

    // ── SyncStatus ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("syncStatus()")
    class SyncStatus {

        @Test
        @DisplayName("gateway now reports CAPTURED → transitions PENDING to SUCCESS")
        void gatewayCaptured_transitionsToSuccess() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            payment.setGatewayOrderId("gw_order_1");
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(gatewayProvider.fetchOrderStatus("gw_order_1")).thenReturn(
                    new GatewayPaymentStatus("gw_pay_1", GatewayPaymentState.CAPTURED, "{}"));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.SUCCESS));

            PaymentResponse result = service.syncStatus(paymentId);

            assertThat(result.getPaymentStatus()).isEqualTo("SUCCESS");
            assertThat(payment.getGatewayPaymentId()).isEqualTo("gw_pay_1");
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_SUCCESS"));
        }

        @Test
        @DisplayName("gateway still has nothing terminal → left PENDING, no save")
        void gatewayStillUnknown_noChange() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            payment.setGatewayOrderId("gw_order_1");
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(gatewayProvider.fetchOrderStatus("gw_order_1")).thenReturn(
                    new GatewayPaymentStatus(null, GatewayPaymentState.UNKNOWN, "{}"));
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.PENDING));

            service.syncStatus(paymentId);

            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("payment not PENDING → no-op, gateway never queried")
        void notPending_noop() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.SUCCESS));

            service.syncStatus(paymentId);

            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("WALLET/CASH payment → no-op, gateway never queried")
        void nonGatewayMethod_noop() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.CASH);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.PENDING));

            service.syncStatus(paymentId);

            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("unknown payment id → throws ResourceNotFoundException")
        void unknownPayment_throws() {
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.syncStatus(paymentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── HasPayableProgress ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasPayableProgress()")
    class HasPayableProgress {

        @Test
        @DisplayName("delegates to the repository's SUCCESS-or-PENDING existence check")
        void delegatesToRepository() {
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(true);

            assertThat(service.hasPayableProgress(orderId)).isTrue();
        }

        @Test
        @DisplayName("no SUCCESS or PENDING payment → false")
        void noProgress_false() {
            when(paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(eq(orderId), any()))
                    .thenReturn(false);

            assertThat(service.hasPayableProgress(orderId)).isFalse();
        }
    }

    // ── Callback ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processCallback()")
    class Callback {

        @Test
        @DisplayName("success=true → marks SUCCESS, publishes event")
        void successCallback() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            when(paymentRepository.findByPaymentReferenceAndDeletedFalse("PAY-TEST-ABCD1234"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.SUCCESS));

            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-ABCD1234");
            req.setSuccess(true);
            req.setGatewayResponse("{\"txnId\":\"xyz\"}");

            service.processCallback(req);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaidAt()).isNotNull();
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_SUCCESS"));
        }

        @Test
        @DisplayName("gatewayPaymentId supplied → linked onto the payment")
        void gatewayPaymentIdSupplied_linked() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            when(paymentRepository.findByPaymentReferenceAndDeletedFalse("PAY-TEST-ABCD1234"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.SUCCESS));

            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-ABCD1234");
            req.setSuccess(true);
            req.setGatewayPaymentId("manual_pay_1");

            service.processCallback(req);

            assertThat(payment.getGatewayPaymentId()).isEqualTo("manual_pay_1");
        }

        @Test
        @DisplayName("success=false → marks FAILED, publishes FAILED event")
        void failedCallback() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.RAZORPAY);
            when(paymentRepository.findByPaymentReferenceAndDeletedFalse("PAY-TEST-ABCD1234"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.FAILED));

            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-ABCD1234");
            req.setSuccess(false);

            service.processCallback(req);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_FAILED"));
        }

        @Test
        @DisplayName("non-PENDING payment → throws PaymentException")
        void alreadyProcessed_throws() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            when(paymentRepository.findByPaymentReferenceAndDeletedFalse("PAY-TEST-ABCD1234"))
                    .thenReturn(Optional.of(payment));

            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-TEST-ABCD1234");
            req.setSuccess(true);

            assertThatThrownBy(() -> service.processCallback(req))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("not in PENDING state");
        }

        @Test
        @DisplayName("unknown reference → throws ResourceNotFoundException")
        void unknownReference_throws() {
            when(paymentRepository.findByPaymentReferenceAndDeletedFalse("PAY-UNKNOWN"))
                    .thenReturn(Optional.empty());

            PaymentCallbackRequest req = new PaymentCallbackRequest();
            req.setPaymentReference("PAY-UNKNOWN");
            req.setSuccess(true);

            assertThatThrownBy(() -> service.processCallback(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Refund ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("SUCCESS WALLET payment → marks REFUNDED, credits wallet, no gateway call")
        void walletRefund_creditsWallet() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.WALLET);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.REFUNDED));

            service.refund(paymentId, customerId, true);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(walletService).creditRefund(customerId, new BigDecimal("150.00"), paymentId);
            verify(eventPublisher).publishEvent(statusEvent(payment, "PAYMENT_REFUNDED"));
            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("SUCCESS UPI payment with a gateway payment id → refunds via the gateway, no wallet credit")
        void upiRefund_refundsViaGateway() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            payment.setGatewayPaymentId("gw_pay_1");
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(gatewayProvider.refund(any())).thenReturn(
                    new GatewayRefund("gw_rfnd_1", GatewayPaymentState.REFUNDED, "{}"));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.REFUNDED));

            service.refund(paymentId, customerId, true);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getGatewayRefundId()).isEqualTo("gw_rfnd_1");
            verify(gatewayProvider).refund(argThat(r -> "gw_pay_1".equals(r.gatewayPaymentId())));
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("SUCCESS UPI payment with no gateway payment id recorded → throws PaymentException")
        void upiRefund_noGatewayPaymentId_throws() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> service.refund(paymentId, customerId, true))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("no gateway payment reference");
            verifyNoInteractions(gatewayProvider);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("CASH payment → marks REFUNDED, no wallet credit, no gateway call")
        void cashRefund_noWalletCredit() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.CASH);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.REFUNDED));

            service.refund(paymentId, customerId, true);

            verify(walletService, never()).creditRefund(any(), any(), any());
            verifyNoInteractions(gatewayProvider);
        }

        @Test
        @DisplayName("PENDING payment → cannot refund")
        void pendingRefund_throws() {
            Payment payment = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> service.refund(paymentId, customerId, true))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Only SUCCESS payments");
        }
    }

    // ── GetSummary ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("returns today's and this month's revenue from repository")
        void returnsRevenueTotals() {
            when(paymentRepository.sumAmountByStatusAndPaidAtBetween(eq(PaymentStatus.SUCCESS), any(), any()))
                    .thenReturn(new BigDecimal("500.00"))
                    .thenReturn(new BigDecimal("12000.00"));

            PaymentSummaryResponse result = service.getSummary();

            assertThat(result.getRevenueToday()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(result.getRevenueThisMonth()).isEqualByComparingTo(new BigDecimal("12000.00"));
            verify(paymentRepository, times(2))
                    .sumAmountByStatusAndPaidAtBetween(eq(PaymentStatus.SUCCESS), any(), any());
        }

        @Test
        @DisplayName("repository returns null → defaults to zero")
        void nullRevenue_defaultsToZero() {
            when(paymentRepository.sumAmountByStatusAndPaidAtBetween(eq(PaymentStatus.SUCCESS), any(), any()))
                    .thenReturn(null);

            PaymentSummaryResponse result = service.getSummary();

            assertThat(result.getRevenueToday()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getRevenueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── GetReport ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getReport()")
    class GetReport {

        @Test
        @DisplayName("maps the repository page into report rows and totals")
        void happyPath() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            var page = new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1);
            when(paymentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(new BigDecimal("150.00"), new BigDecimal("150.00"));

            ReportPage<PaymentReportRow, PaymentReportSummary> result = service.getReport(
                    null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getPaymentId()).isEqualTo(paymentId);
            assertThat(result.getContent().get(0).getOrderId()).isEqualTo(orderId);
            assertThat(result.getContent().get(0).getPaymentStatus()).isEqualTo("SUCCESS");
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getSummary().getTotalPayments()).isEqualTo(1);
            assertThat(result.getSummary().getTotalAmount()).isEqualByComparingTo("150.00");
            assertThat(result.getSummary().getSuccessAmount()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("no matching payments → empty content with zeroed summary")
        void noResults() {
            var page = new PageImpl<Payment>(List.of(), PageRequest.of(0, 20), 0);
            when(paymentRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(BigDecimal.ZERO);

            ReportPage<PaymentReportRow, PaymentReportSummary> result = service.getReport(
                    LocalDate.now().minusDays(7), LocalDate.now(), PaymentStatus.FAILED, customerId,
                    PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalPayments()).isZero();
            assertThat(result.getSummary().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getSummary().getSuccessAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("export()")
    class Export {

        @Test
        @DisplayName("CSV format → streams matching rows as CSV")
        void csv_streamsMatchingRows() throws Exception {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            var firstPage = new PageImpl<>(List.of(payment),
                    PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("createdAt").ascending()), 1);
            var emptyPage = new PageImpl<>(List.<Payment>of());
            when(paymentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(firstPage, emptyPage);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, null, null, null, null, "createdAt", true);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).contains("PAY-TEST-ABCD1234");
        }

        @Test
        @DisplayName("no matches → writes header only")
        void noMatches_writesHeaderOnly() throws Exception {
            when(paymentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.<Payment>of()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, null, null, null, null, "createdAt", false);

            assertThat(out.toString(java.nio.charset.StandardCharsets.UTF_8).trim())
                    .isEqualTo("Payment Reference,Order ID,Customer ID,Amount,Payment Method,Payment Status,Paid At,Created At");
        }
    }

    @Nested
    @DisplayName("getRevenueTrend()")
    class GetRevenueTrend {

        @Test
        @DisplayName("maps already-aggregated repository rows into trend points")
        void mapsRows() {
            PaymentRepository.RevenueTrendRow row = mock(PaymentRepository.RevenueTrendRow.class);
            when(row.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(row.getRevenue()).thenReturn(new BigDecimal("500.00"));
            when(paymentRepository.findRevenueTrend(eq("day"), any(), any())).thenReturn(List.of(row));

            TrendSeries<RevenueTrendPoint> result = service.getRevenueTrend(
                    Granularity.DAILY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertThat(result.getGranularity()).isEqualTo(Granularity.DAILY);
            assertThat(result.getPoints()).hasSize(1);
            assertThat(result.getPoints().get(0).getPeriod()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(result.getPoints().get(0).getRevenue()).isEqualByComparingTo("500.00");
            verify(paymentRepository).findRevenueTrend("day",
                    LocalDate.of(2026, 1, 1).atStartOfDay(), LocalDate.of(2026, 2, 1).atStartOfDay());
        }

        @Test
        @DisplayName("no rows → empty points list")
        void noRows_emptyPoints() {
            when(paymentRepository.findRevenueTrend(eq("month"), any(), any())).thenReturn(List.of());

            TrendSeries<RevenueTrendPoint> result = service.getRevenueTrend(Granularity.MONTHLY, null, null);

            assertThat(result.getPoints()).isEmpty();
            verify(paymentRepository).findRevenueTrend("month", null, null);
        }
    }

    @Nested
    @DisplayName("getPaymentAnalytics()")
    class GetPaymentAnalytics {

        @Test
        @DisplayName("folds per-status rows for the same period into one point")
        void foldsRowsByPeriod() {
            PaymentRepository.PaymentAnalyticsRow success = mock(PaymentRepository.PaymentAnalyticsRow.class);
            when(success.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(success.getStatus()).thenReturn("SUCCESS");
            when(success.getTxnCount()).thenReturn(3L);
            when(success.getAmount()).thenReturn(new BigDecimal("300.00"));

            PaymentRepository.PaymentAnalyticsRow failed = mock(PaymentRepository.PaymentAnalyticsRow.class);
            when(failed.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(failed.getStatus()).thenReturn("FAILED");
            when(failed.getTxnCount()).thenReturn(1L);
            when(failed.getAmount()).thenReturn(new BigDecimal("50.00"));

            when(paymentRepository.findPaymentAnalytics(eq("day"), any(), any())).thenReturn(List.of(success, failed));

            TrendSeries<PaymentAnalyticsPoint> result = service.getPaymentAnalytics(Granularity.DAILY, null, null);

            assertThat(result.getPoints()).hasSize(1);
            PaymentAnalyticsPoint point = result.getPoints().get(0);
            assertThat(point.getPeriod()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(point.getTotalPayments()).isEqualTo(4);
            assertThat(point.getTotalAmount()).isEqualByComparingTo("350.00");
            assertThat(point.getSuccessCount()).isEqualTo(3);
            assertThat(point.getFailedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("no rows → empty points list")
        void noRows_emptyPoints() {
            when(paymentRepository.findPaymentAnalytics(eq("year"), any(), any())).thenReturn(List.of());

            TrendSeries<PaymentAnalyticsPoint> result = service.getPaymentAnalytics(Granularity.YEARLY, null, null);

            assertThat(result.getPoints()).isEmpty();
        }
    }
}
