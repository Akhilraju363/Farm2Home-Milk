package com.farm2home.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryEventConsumerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AuditLogService auditLogService;

    // Constructed manually (not @InjectMocks) so the consumer gets a real, working ObjectMapper
    // rather than an unstubbed mock - it actually needs to parse JSON in these tests.
    private DeliveryEventConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new DeliveryEventConsumer(orderRepository, auditLogService, objectMapper);
    }

    private Order orderWith(OrderStatus status) {
        Order order = new Order();
        order.setId(orderId);
        order.setOrderNumber("ORD-2026-000001");
        order.setStatus(status);
        return order;
    }

    private String eventJson(String status) throws Exception {
        return objectMapper.writeValueAsString(
                DeliveryEvent.builder().eventType("DELIVERY_" + status).orderId(orderId).status(status).build());
    }

    @Nested
    class ValidTransition {

        @Test
        void assignedEvent_transitionsPendingOrderToAssigned() throws Exception {
            Order order = orderWith(OrderStatus.PENDING);
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));

            consumer.consume(eventJson("ASSIGNED"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            verify(orderRepository).save(order);
            verify(auditLogService).record(any(AuditEntry.class));
        }

        @Test
        void deliveredEvent_transitionsOutForDeliveryOrderToDelivered() throws Exception {
            Order order = orderWith(OrderStatus.OUT_FOR_DELIVERY);
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));

            consumer.consume(eventJson("DELIVERED"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            verify(orderRepository).save(order);
        }
    }

    @Nested
    class NoOp {

        @Test
        void failedStatus_hasNoOrderStatusMapping_leftUnapplied() throws Exception {
            consumer.consume(eventJson("FAILED"));

            verify(orderRepository, never()).findByIdAndDeletedFalse(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void unknownOrder_skipped() throws Exception {
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.empty());

            consumer.consume(eventJson("ASSIGNED"));

            verify(orderRepository, never()).save(any());
        }

        @Test
        void invalidTransition_fromTerminalStatus_skipped() throws Exception {
            Order order = orderWith(OrderStatus.DELIVERED);
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));

            consumer.consume(eventJson("ASSIGNED"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            verify(orderRepository, never()).save(any());
        }

        @Test
        void duplicateRedelivery_sameStatus_skipped() throws Exception {
            Order order = orderWith(OrderStatus.ASSIGNED);
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));

            consumer.consume(eventJson("ASSIGNED"));

            verify(orderRepository, never()).save(any());
        }

        @Test
        void malformedMessage_doesNotThrow() {
            consumer.consume("not valid json");
            verify(orderRepository, never()).findByIdAndDeletedFalse(any());
        }
    }
}
