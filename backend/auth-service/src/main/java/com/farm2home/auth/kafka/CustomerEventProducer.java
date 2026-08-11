package com.farm2home.auth.kafka;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.events.customer.CustomerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventProducer {

    private static final String TOPIC = "customer.events";

    private final KafkaTemplate<String, CustomerEvent> customerKafkaTemplate;

    /** Best-effort, fire-and-forget publish - runs on kafkaEventExecutor (see KafkaConfig), never
     *  the register() request thread, so it's never allowed to make the HTTP caller wait on
     *  Kafka's health. KafkaTemplate.send() can throw synchronously (not just via the returned
     *  future) when it can't resolve topic/broker metadata in time, e.g. Kafka is down - see
     *  doSend()'s partitionsFor() call, bounded by producer property max.block.ms. If that
     *  happens, the customer-service profile this event would have created is simply delayed
     *  until Kafka is back and the event can be retried/resent. */
    @Async("kafkaEventExecutor")
    public void publishCustomerCreated(User user, String firstName, String lastName) {
        CustomerEvent event = CustomerEvent.builder()
                .eventType(EmailTemplateConstants.EVENT_CUSTOMER_CREATED)
                .customerId(user.getId())
                .customerName(firstName + " " + lastName)
                .firstName(firstName)
                .lastName(lastName)
                .mobile(user.getMobile())
                .email(user.getEmail())
                .occurredAt(LocalDateTime.now())
                .build();

        try {
            customerKafkaTemplate.send(TOPIC, user.getId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish CustomerEvent for user {}: {}",
                                    user.getId(), ex.getMessage());
                        } else {
                            log.debug("Published CustomerEvent [CUSTOMER_CREATED] for user {}", user.getId());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish CustomerEvent for user {} (Kafka unreachable?): {}",
                    user.getId(), ex.getMessage());
        }
    }
}
