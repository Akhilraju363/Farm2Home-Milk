package com.farm2home.payment.service;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.exception.PaymentException;
import com.farm2home.payment.exception.ResourceNotFoundException;
import com.farm2home.payment.kafka.PaymentEventProducer;
import com.farm2home.payment.mapper.PaymentMapper;
import com.farm2home.payment.service.impl.PaymentServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private WalletService walletService;
    @Mock private PaymentMapper mapper;
    @Mock private PaymentEventProducer eventProducer;

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

    // ── Initiate ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiate()")
    class Initiate {

        @Test
        @DisplayName("UPI payment → stays PENDING, no wallet debit")
        void upiPayment_createsPendingRecord() {
            when(paymentRepository.existsByOrderIdAndPaymentStatusAndDeletedFalse(orderId, PaymentStatus.SUCCESS))
                    .thenReturn(false);
            Payment saved = buildPayment(PaymentStatus.PENDING, PaymentMethod.UPI);
            when(paymentRepository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(buildResponse(PaymentStatus.PENDING));

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            PaymentResponse result = service.initiate(req, customerId);

            assertThat(result.getPaymentStatus()).isEqualTo("PENDING");
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("WALLET payment → debits wallet, marks SUCCESS immediately")
        void walletPayment_debitsThenSuccess() {
            when(paymentRepository.existsByOrderIdAndPaymentStatusAndDeletedFalse(orderId, PaymentStatus.SUCCESS))
                    .thenReturn(false);
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
            verify(eventProducer).publishPaymentEvent(saved, "PAYMENT_SUCCESS");
        }

        @Test
        @DisplayName("order already paid → throws PaymentException")
        void duplicatePayment_throws() {
            when(paymentRepository.existsByOrderIdAndPaymentStatusAndDeletedFalse(orderId, PaymentStatus.SUCCESS))
                    .thenReturn(true);

            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(orderId);
            req.setAmount(new BigDecimal("150.00"));
            req.setPaymentMethod(PaymentMethod.UPI);

            assertThatThrownBy(() -> service.initiate(req, customerId))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("already been paid");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("admin sets explicit customerId → uses it instead of caller")
        void adminSetsCustomerId() {
            UUID targetCustomer = UUID.randomUUID();
            when(paymentRepository.existsByOrderIdAndPaymentStatusAndDeletedFalse(orderId, PaymentStatus.SUCCESS))
                    .thenReturn(false);
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
            verify(eventProducer).publishPaymentEvent(payment, "PAYMENT_SUCCESS");
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
            verify(eventProducer).publishPaymentEvent(payment, "PAYMENT_FAILED");
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
        @DisplayName("SUCCESS UPI payment → marks REFUNDED, credits wallet")
        void upiRefund_creditsWallet() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.UPI);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.REFUNDED));

            service.refund(paymentId, customerId, true);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(walletService).creditRefund(customerId, new BigDecimal("150.00"), paymentId);
            verify(eventProducer).publishPaymentEvent(payment, "PAYMENT_REFUNDED");
        }

        @Test
        @DisplayName("CASH payment → marks REFUNDED, no wallet credit")
        void cashRefund_noWalletCredit() {
            Payment payment = buildPayment(PaymentStatus.SUCCESS, PaymentMethod.CASH);
            when(paymentRepository.findByIdAndDeletedFalse(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(mapper.toResponse(payment)).thenReturn(buildResponse(PaymentStatus.REFUNDED));

            service.refund(paymentId, customerId, true);

            verify(walletService, never()).creditRefund(any(), any(), any());
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
}
