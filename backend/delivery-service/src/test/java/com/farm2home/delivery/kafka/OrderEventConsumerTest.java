package com.farm2home.delivery.kafka;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.service.impl.PartnerSelectionServiceImpl;
import com.farm2home.events.order.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Automatic assignment - triggered by ORDER_CREATED, see the class's own Javadoc for why this
 *  stays event-driven (avoiding a circular HTTP dependency with order-service). These tests cover
 *  section 27's assignment matrix (correct order/route/partner/status, duplicate protection) at
 *  the consumer level - PartnerSelectionServiceImplTest covers the selection algorithm itself in
 *  isolation. Kafka transport itself (message delivery, deserialization, consumer-group
 *  rebalancing) is NOT exercised here or anywhere in this dev environment (no broker running) -
 *  see the final report's explicit "Kafka E2E: NOT VERIFIED" note. This only proves the consumer's
 *  OWN logic is correct once Spring Kafka hands it a deserialized OrderEvent. */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private DeliveryRouteRepository routeRepository;
    @Mock private PartnerSelectionServiceImpl partnerSelectionService;
    @Mock private DeliveryEventProducer eventProducer;
    @InjectMocks private OrderEventConsumer consumer;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID routeId = UUID.randomUUID();

    private OrderEvent.OrderEventBuilder baseEvent() {
        return OrderEvent.builder()
                .eventType("ORDER_CREATED")
                .orderId(orderId)
                .orderNumber("ORD-2026-100042")
                .customerId(customerId)
                .deliveryRouteId(routeId);
    }

    private DeliveryRoute buildRoute() {
        return DeliveryRoute.builder().id(routeId).routeName("Farm2Home North").routeCode("F2H-NORTH").active(true).build();
    }

    private DeliveryPartner buildPartner() {
        return DeliveryPartner.builder().id(UUID.randomUUID()).name("Rahul Kumar").active(true).build();
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("eligible partner found -> creates an autoAssigned=true assignment using the ORDER's route, publishes DELIVERY_ASSIGNED")
        void createsAutoAssignment() {
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(false);
            DeliveryRoute route = buildRoute();
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(route));
            DeliveryPartner partner = buildPartner();
            when(partnerSelectionService.selectLeastLoadedPartner(routeId)).thenReturn(Optional.of(partner));
            when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.consume(baseEvent().build());

            ArgumentCaptor<DeliveryAssignment> captor = ArgumentCaptor.forClass(DeliveryAssignment.class);
            verify(assignmentRepository).save(captor.capture());
            DeliveryAssignment saved = captor.getValue();
            assertThat(saved.getOrderId()).isEqualTo(orderId);
            assertThat(saved.getCustomerId()).isEqualTo(customerId);
            assertThat(saved.getOrderNumber()).isEqualTo("ORD-2026-100042");
            assertThat(saved.getDeliveryPartner()).isEqualTo(partner);
            assertThat(saved.getRoute()).isEqualTo(route);
            assertThat(saved.isAutoAssigned()).isTrue();

            verify(eventProducer).publishDeliveryEvent(saved, "DELIVERY_ASSIGNED");
        }

        @Test
        @DisplayName("uses Order.deliveryRouteId, never any other route - the exact bug this fix targets " +
                "(the assignment's route must equal the order's route, never e.g. a partner's own route)")
        void usesOrdersOwnRoute_notAnySubstitute() {
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(false);
            DeliveryRoute orderRoute = buildRoute();
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(orderRoute));
            DeliveryPartner partner = buildPartner();
            when(partnerSelectionService.selectLeastLoadedPartner(routeId)).thenReturn(Optional.of(partner));
            when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.consume(baseEvent().build());

            verify(routeRepository).findByIdAndDeletedFalse(routeId);
            ArgumentCaptor<DeliveryAssignment> captor = ArgumentCaptor.forClass(DeliveryAssignment.class);
            verify(assignmentRepository).save(captor.capture());
            assertThat(captor.getValue().getRoute().getId()).isEqualTo(routeId);
        }
    }

    @Nested
    @DisplayName("order remains unassigned, never a hard failure")
    class UnassignedCases {

        @Test
        @DisplayName("no eligible partner on the route -> no assignment created, no exception propagates")
        void noEligiblePartner_leavesOrderUnassigned() {
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(buildRoute()));
            when(partnerSelectionService.selectLeastLoadedPartner(routeId)).thenReturn(Optional.empty());

            consumer.consume(baseEvent().build());

            verify(assignmentRepository, never()).save(any());
            verify(eventProducer, never()).publishDeliveryEvent(any(), any());
        }

        @Test
        @DisplayName("order has no automatically-selected route (deliveryRouteId null) -> skipped, partner selection never even attempted")
        void noRouteOnOrder_skipped() {
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(false);

            consumer.consume(baseEvent().deliveryRouteId(null).build());

            verify(partnerSelectionService, never()).selectLeastLoadedPartner(any());
            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("order's route no longer exists (soft-deleted) -> skipped, not a crash")
        void routeDeleted_skipped() {
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(false);
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.empty());

            consumer.consume(baseEvent().build());

            verify(partnerSelectionService, never()).selectLeastLoadedPartner(any());
            verify(assignmentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("idempotency and event filtering")
    class Guards {

        @Test
        @DisplayName("assignment already exists for this order -> skipped without re-querying partners (idempotent redelivery)")
        void alreadyAssigned_skipped() {
            when(assignmentRepository.existsByOrderId(orderId)).thenReturn(true);

            consumer.consume(baseEvent().build());

            verify(routeRepository, never()).findByIdAndDeletedFalse(any());
            verify(partnerSelectionService, never()).selectLeastLoadedPartner(any());
            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("non-ORDER_CREATED event type -> ignored entirely")
        void wrongEventType_ignored() {
            consumer.consume(baseEvent().eventType("ORDER_CANCELLED").build());

            verify(assignmentRepository, never()).existsByOrderId(any());
            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("an unexpected exception during processing is caught, never propagates out of the listener")
        void unexpectedException_doesNotPropagate() {
            when(assignmentRepository.existsByOrderId(orderId)).thenThrow(new RuntimeException("DB hiccup"));

            org.assertj.core.api.Assertions.assertThatCode(() -> consumer.consume(baseEvent().build()))
                    .doesNotThrowAnyException();
        }
    }
}
