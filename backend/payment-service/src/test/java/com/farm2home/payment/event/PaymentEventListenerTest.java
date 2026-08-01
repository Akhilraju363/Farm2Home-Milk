package com.farm2home.payment.event;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.kafka.PaymentEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock private PaymentEventProducer eventProducer;

    @Test
    @DisplayName("onPaymentStatusChanged() delegates straight to the Kafka producer")
    void delegatesToProducer() {
        PaymentEventListener listener = new PaymentEventListener(eventProducer);
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .paymentReference("PAY-TEST")
                .amount(new BigDecimal("150.00"))
                .paymentMethod(PaymentMethod.UPI)
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        listener.onPaymentStatusChanged(new PaymentStatusChangedEvent(this, payment, "PAYMENT_SUCCESS"));

        verify(eventProducer).publishPaymentEvent(payment, "PAYMENT_SUCCESS");
    }
}
