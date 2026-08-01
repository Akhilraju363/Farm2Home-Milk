package com.farm2home.order.kafka;

import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.constants.KafkaTopics;
import com.farm2home.events.order.OrderEvent;
import com.farm2home.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private static final String ORDER_EVENTS_TOPIC = KafkaTopics.ORDER_EVENTS;

    private final KafkaTemplate<String, OrderEvent> orderKafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderEvent event = OrderEvent.builder()
                .eventType(EmailTemplateConstants.EVENT_ORDER_CREATED)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .subscriptionId(order.getSubscriptionId())
                .orderDate(order.getOrderDate())
                .orderType(order.getOrderType().name())
                .totalAmount(order.getTotalAmount())
                .items(order.getItems() == null ? Collections.emptyList() :
                        order.getItems().stream().map(item -> OrderEvent.OrderItem.builder()
                                .milkType(item.getMilkType().name())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .totalPrice(item.getTotalPrice())
                                .build()).toList())
                .occurredAt(LocalDateTime.now())
                .build();

        orderKafkaTemplate.send(ORDER_EVENTS_TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderEvent [ORDER_CREATED] for order {}: {}",
                                order.getOrderNumber(), ex.getMessage());
                    } else {
                        log.info("Published OrderEvent [ORDER_CREATED] for order {}", order.getOrderNumber());
                    }
                });
    }
}
