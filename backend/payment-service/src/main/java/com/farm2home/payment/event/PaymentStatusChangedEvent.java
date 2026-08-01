package com.farm2home.payment.event;

import com.farm2home.payment.domain.entity.Payment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code PaymentServiceImpl} whenever a payment reaches SUCCESS/FAILED/REFUNDED,
 * from inside the same transaction that persisted the change. {@code PaymentEventListener} picks
 * this up only after that transaction actually commits, and off the request thread — so a Kafka
 * hiccup or slow broker never adds latency to the HTTP response, and a payment whose transaction
 * later rolls back never gets an event published for a change that didn't happen.
 */
@Getter
public class PaymentStatusChangedEvent extends ApplicationEvent {

    private final Payment payment;
    private final String eventType;

    public PaymentStatusChangedEvent(Object source, Payment payment, String eventType) {
        super(source);
        this.payment = payment;
        this.eventType = eventType;
    }
}
