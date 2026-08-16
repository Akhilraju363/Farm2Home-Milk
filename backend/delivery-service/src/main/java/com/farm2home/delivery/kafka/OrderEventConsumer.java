package com.farm2home.delivery.kafka;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.service.impl.PartnerSelectionServiceImpl;
import com.farm2home.events.order.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Automatic delivery-partner assignment - triggered by order-service's ORDER_CREATED event, using
 * the route order-service already selected for the order (Order.deliveryRouteId, carried on the
 * event - see OrderEvent's own Javadoc). This is an ADDITIONAL workflow alongside manual
 * assignment (DeliveryAssignmentServiceImpl.manualAssign), not a replacement - if no eligible
 * partner exists, the order simply stays unassigned for an admin to assign manually later; this
 * consumer never fails the order itself, only skips creating an assignment.
 *
 * <p>Architecture note: this stays event-driven specifically to avoid a circular HTTP dependency.
 * delivery-service already calls order-service (OrderServiceClient, used by manualAssign);
 * order-service calling delivery-service directly here would close that loop. Kafka already
 * carries everything this consumer needs (deliveryRouteId included), so no HTTP call back to
 * order-service was needed for this feature.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryRouteRepository routeRepository;
    private final PartnerSelectionServiceImpl partnerSelectionService;
    private final DeliveryEventProducer eventProducer;

    @KafkaListener(
            topics = "order.events",
            groupId = "delivery-service",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(OrderEvent event) {
        try {
            if (!"ORDER_CREATED".equals(event.getEventType())) return;

            // Idempotency: redelivery/reprocessing of the same event must never create a second
            // assignment (manualAssign() also guards this independently, so a race between an
            // admin manually assigning and this consumer processing the same order is safe either
            // way - whichever commits first wins, the other sees existsByOrderId=true).
            if (assignmentRepository.existsByOrderId(event.getOrderId())) {
                log.debug("Assignment already exists for order {} - skipping automatic assignment", event.getOrderId());
                return;
            }

            if (event.getDeliveryRouteId() == null) {
                log.warn("Order {} has no automatically-selected route (delivery-radius/route-coverage gap) - "
                        + "left unassigned for manual assignment", event.getOrderId());
                return;
            }

            // The order's own already-determined route is authoritative - never recalculated or
            // substituted here (e.g. with a partner's own route), which is what the old version of
            // this consumer incorrectly did. A route that existed at order-creation time but is
            // somehow gone now (soft-deleted) is treated the same as "no route": skip, don't guess.
            Optional<DeliveryRoute> route = routeRepository.findByIdAndDeletedFalse(event.getDeliveryRouteId());
            if (route.isEmpty()) {
                log.warn("Order {}'s route {} no longer exists - left unassigned for manual assignment",
                        event.getOrderId(), event.getDeliveryRouteId());
                return;
            }

            // See PartnerSelectionServiceImpl's own Javadoc for eligibility/workload/tie-break
            // rules and the concurrency-safety strategy (row-level locking, held for the rest of
            // THIS transaction - which is why this method itself must stay @Transactional).
            Optional<DeliveryPartner> partner = partnerSelectionService.selectLeastLoadedPartner(event.getDeliveryRouteId());
            if (partner.isEmpty()) {
                log.warn("No active delivery partner available on route {} for order {} - "
                        + "left unassigned for manual assignment", event.getDeliveryRouteId(), event.getOrderId());
                return;
            }

            DeliveryAssignment assignment = assignmentRepository.save(DeliveryAssignment.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .orderNumber(event.getOrderNumber())
                    .deliveryPartner(partner.get())
                    .route(route.get())
                    .autoAssigned(true)
                    .build());

            // Same event/notification path manualAssign() already uses (DELIVERY_ASSIGNED) - no
            // separate notification type or channel for the automatic case.
            eventProducer.publishDeliveryEvent(assignment, "DELIVERY_ASSIGNED");
            log.info("Auto-assigned order {} to partner {} on route {}",
                    event.getOrderId(), partner.get().getName(), route.get().getRouteCode());

        } catch (Exception e) {
            // An exception here must never crash the Kafka listener thread or block subsequent
            // messages - a failed automatic assignment just leaves the order unassigned, same as
            // "no eligible partner", not a hard failure anywhere else in the system.
            log.error("Failed to auto-assign order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
