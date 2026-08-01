package com.farm2home.notification.kafka;

import com.farm2home.common.core.constants.KafkaTopics;
import com.farm2home.notification.dto.KafkaEventDto;
import com.farm2home.notification.service.impl.NotificationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationServiceImpl notificationService;

    @KafkaListener(
        topics = {KafkaTopics.ORDER_EVENTS, KafkaTopics.DELIVERY_EVENTS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.SUBSCRIPTION_EVENTS,
                KafkaTopics.CUSTOMER_EVENTS, KafkaTopics.INVENTORY_EVENTS, KafkaTopics.OTP_EVENTS},
        groupId = "${spring.kafka.consumer.group-id:notification-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(KafkaEventDto event) {
        if (event == null || event.getEventType() == null) return;
        log.debug("Received notification event: {}", event.getEventType());
        try {
            notificationService.process(event);
        } catch (Exception ex) {
            log.error("Error processing notification event {}: {}", event.getEventType(), ex.getMessage(), ex);
        }
    }
}
