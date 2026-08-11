package com.farm2home.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.KafkaTopics;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Keeps {@code Order.status} in sync with delivery-service's own {@code DeliveryAssignment.status}
 * - the delivery partner's real workflow (PATCH /delivery/assignments/{id}/status, correctly
 * ownership-scoped there) lives entirely in delivery-service, not here. order-service never calls
 * back into delivery-service; it only reacts to what delivery-service already decided, same
 * decoupling as {@link SubscriptionEventConsumer}.
 *
 * FAILED has no corresponding OrderStatus (there's no OrderStatus.FAILED, and auto-cancelling on a
 * failed delivery attempt isn't obviously correct - it may just be retried/reassigned) - failure
 * events are logged but intentionally left for a human to act on, not auto-applied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventConsumer {

    private static final Map<String, OrderStatus> STATUS_MAP = Map.of(
            "ASSIGNED", OrderStatus.ASSIGNED,
            "OUT_FOR_DELIVERY", OrderStatus.OUT_FOR_DELIVERY,
            "DELIVERED", OrderStatus.DELIVERED
    );

    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.DELIVERY_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            DeliveryEvent event = objectMapper.readValue(message, DeliveryEvent.class);
            log.info("Received delivery event: {} for orderId {} (status={})",
                    event.getEventType(), event.getOrderId(), event.getStatus());
            handleEvent(event);
        } catch (Exception e) {
            log.error("Failed to process delivery event: {}", e.getMessage(), e);
        }
    }

    private void handleEvent(DeliveryEvent event) {
        if (event.getOrderId() == null || event.getStatus() == null) {
            return;
        }

        OrderStatus newStatus = STATUS_MAP.get(event.getStatus());
        if (newStatus == null) {
            log.warn("Delivery event status '{}' for order {} has no OrderStatus mapping - left unapplied",
                    event.getStatus(), event.getOrderId());
            return;
        }

        Order order = orderRepository.findByIdAndDeletedFalse(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Delivery event for unknown/deleted order {} - skipped", event.getOrderId());
            return;
        }

        OrderStatus previousStatus = order.getStatus();
        if (!previousStatus.canTransitionTo(newStatus)) {
            // Not an error: duplicate redelivery, out-of-order arrival, or the order was already
            // moved elsewhere (e.g. admin-cancelled) - safe to no-op rather than fail the consumer.
            log.debug("Ignoring delivery-sync transition {} -> {} for order {} (not a valid transition from {})",
                    previousStatus, newStatus, order.getId(), previousStatus);
            return;
        }

        order.setStatus(newStatus);
        orderRepository.save(order);
        log.info("Order {} status synced to {} from delivery-service", order.getOrderNumber(), newStatus);

        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("Order")
                .entityId(order.getId().toString())
                .oldValue(previousStatus.name())
                .newValue(newStatus.name())
                .details("Order " + order.getOrderNumber() + " status synced from delivery-service: "
                        + previousStatus + " -> " + newStatus)
                .build());
    }
}
