package com.farm2home.auth.kafka;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.events.customer.CustomerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventProducer {

    private static final String TOPIC = "customer.events";

    private final KafkaTemplate<String, CustomerEvent> customerKafkaTemplate;

    public void publishCustomerCreated(User user, String customerName) {
        CustomerEvent event = CustomerEvent.builder()
                .eventType(EmailTemplateConstants.EVENT_CUSTOMER_CREATED)
                .customerId(user.getId())
                .customerName(customerName)
                .mobile(user.getMobile())
                .email(user.getEmail())
                .occurredAt(LocalDateTime.now())
                .build();

        customerKafkaTemplate.send(TOPIC, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CustomerEvent for user {}: {}",
                                user.getId(), ex.getMessage());
                    } else {
                        log.debug("Published CustomerEvent [CUSTOMER_CREATED] for user {}", user.getId());
                    }
                });
    }
}
