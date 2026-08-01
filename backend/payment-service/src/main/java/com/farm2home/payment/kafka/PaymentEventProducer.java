package com.farm2home.payment.kafka;

import com.farm2home.common.core.constants.KafkaTopics;
import com.farm2home.events.payment.PaymentEvent;
import com.farm2home.payment.domain.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private static final String TOPIC = KafkaTopics.PAYMENT_EVENTS;

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publishPaymentEvent(Payment payment, String eventType) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType(eventType)
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .paymentReference(payment.getPaymentReference())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(TOPIC, payment.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment event [{}] for order {}: {}",
                                eventType, payment.getOrderId(), ex.getMessage());
                    } else {
                        log.debug("Published payment event [{}] for order {}", eventType, payment.getOrderId());
                    }
                });
    }
}
