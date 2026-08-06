package com.farm2home.delivery.kafka;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.events.delivery.DeliveryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventProducer {

    private static final String TOPIC = "delivery.events";

    private final KafkaTemplate<String, DeliveryEvent> kafkaTemplate;

    public void publishDeliveryEvent(DeliveryAssignment assignment, String eventType) {
        DeliveryEvent event = DeliveryEvent.builder()
                .eventType(eventType)
                .assignmentId(assignment.getId())
                .orderId(assignment.getOrderId())
                .orderNumber(assignment.getOrderNumber())
                .customerId(assignment.getCustomerId())
                .deliveryPartnerId(assignment.getDeliveryPartner().getId())
                .deliveryPartnerName(assignment.getDeliveryPartner().getName())
                .status(assignment.getStatus().name())
                .failureReason(assignment.getFailureReason())
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(TOPIC, assignment.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish delivery event [{}] for order {}: {}",
                                eventType, assignment.getOrderId(), ex.getMessage());
                    }
                });
    }
}
