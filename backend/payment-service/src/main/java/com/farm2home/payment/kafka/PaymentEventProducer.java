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

        // send() can throw synchronously (e.g. on metadata-wait timeout when no broker is
        // reachable) before ever returning the future that whenComplete() observes. This
        // producer is invoked from an @Async listener (see PaymentEventListener), so an
        // uncaught exception here wouldn't corrupt the already-committed Payment row - but it
        // would still leave an unhandled exception on the async executor and skip the log below.
        try {
            kafkaTemplate.send(TOPIC, payment.getOrderId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish payment event [{}] for order {}: {}",
                                    eventType, payment.getOrderId(), ex.getMessage());
                        } else {
                            log.debug("Published payment event [{}] for order {}", eventType, payment.getOrderId());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish payment event [{}] for order {}: {}",
                    eventType, payment.getOrderId(), ex.getMessage());
        }
    }
}
