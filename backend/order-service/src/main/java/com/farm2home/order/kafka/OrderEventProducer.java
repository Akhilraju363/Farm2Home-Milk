package com.farm2home.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm2home.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private static final String ORDER_EVENTS_TOPIC = "order.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrderCreated(Order order) {
        try {
            OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .customerId(order.getCustomerId())
                    .subscriptionId(order.getSubscriptionId())
                    .orderDate(order.getOrderDate())
                    .orderType(order.getOrderType().name())
                    .totalAmount(order.getTotalAmount())
                    .items(order.getItems() == null ? Collections.emptyList() :
                            order.getItems().stream().map(item -> OrderCreatedEvent.OrderItemEvent.builder()
                                    .milkType(item.getMilkType().name())
                                    .quantity(item.getQuantity())
                                    .unitPrice(item.getUnitPrice())
                                    .totalPrice(item.getTotalPrice())
                                    .build()).toList())
                    .build();

            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_EVENTS_TOPIC, order.getId().toString(), payload);
            log.info("Published OrderCreatedEvent for order {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to publish OrderCreatedEvent for order {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }
    }
}
