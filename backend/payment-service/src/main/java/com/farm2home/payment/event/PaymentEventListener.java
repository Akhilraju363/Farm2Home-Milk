package com.farm2home.payment.event;

import com.farm2home.payment.kafka.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Split out from PaymentServiceImpl purely so {@code @Async} actually applies — like
 * {@code common-core}'s {@code AuditPersister}, both {@code @Async} and
 * {@code @TransactionalEventListener} are proxy-based, and a bean invoking its own annotated
 * method (self-invocation) bypasses the proxy entirely.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventProducer eventProducer;

    @Async("paymentEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        eventProducer.publishPaymentEvent(event.getPayment(), event.getEventType());
    }
}
