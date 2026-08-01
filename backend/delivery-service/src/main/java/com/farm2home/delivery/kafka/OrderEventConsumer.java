package com.farm2home.delivery.kafka;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.events.order.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerRepository partnerRepository;
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

            // Skip if already assigned (idempotency)
            if (assignmentRepository.existsByOrderId(event.getOrderId())) {
                log.debug("Assignment already exists for order {}", event.getOrderId());
                return;
            }

            List<DeliveryPartner> candidates = partnerRepository
                    .findLeastLoadedPartners(PageRequest.of(0, 1));

            if (candidates.isEmpty()) {
                log.warn("No active delivery partners available for order {}", event.getOrderId());
                return;
            }

            DeliveryPartner partner = candidates.get(0);
            DeliveryAssignment assignment = DeliveryAssignment.builder()
                    .orderId(event.getOrderId())
                    .deliveryPartner(partner)
                    .route(partner.getRoute())
                    .build();

            // Route is optional at assignment time — partner may not have one pre-assigned
            if (partner.getRoute() == null) {
                log.warn("Partner {} has no route assigned; creating assignment without route for order {}",
                        partner.getId(), event.getOrderId());
                return;
            }

            DeliveryAssignment saved = assignmentRepository.save(assignment);
            eventProducer.publishDeliveryEvent(saved, "DELIVERY_ASSIGNED");
            log.info("Auto-assigned order {} to partner {}", event.getOrderId(), partner.getName());

        } catch (Exception e) {
            log.error("Failed to auto-assign order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
