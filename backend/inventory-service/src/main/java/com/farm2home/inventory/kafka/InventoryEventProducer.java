package com.farm2home.inventory.kafka;

import com.farm2home.events.inventory.InventoryEvent;
import com.farm2home.inventory.domain.entity.InventoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private static final String TOPIC = "inventory.events";

    private final KafkaTemplate<String, InventoryEvent> inventoryKafkaTemplate;

    public void publishInventoryUpdated(InventoryItem item, BigDecimal previousQuantity) {
        publish(item, previousQuantity);
    }

    /** Periodic sweep alert (no transaction just occurred, so there's no real "previous"
     *  quantity - report it unchanged). Reuses the INVENTORY_UPDATED eventType so
     *  notification-service's existing low-stock branch picks it up without any changes. */
    public void publishLowStockAlert(InventoryItem item) {
        publish(item, item.getQuantity());
    }

    private void publish(InventoryItem item, BigDecimal previousQuantity) {
        InventoryEvent event = InventoryEvent.builder()
                .eventType("INVENTORY_UPDATED")
                .itemId(item.getId())
                .itemName(item.getItemName())
                .itemType(item.getItemType().name())
                .quantity(item.getQuantity())
                .previousQuantity(previousQuantity)
                .unit(item.getUnit().name())
                .reorderLevel(item.getReorderLevel())
                .lowStock(item.getQuantity().compareTo(item.getReorderLevel()) <= 0)
                .occurredAt(LocalDateTime.now())
                .build();

        inventoryKafkaTemplate.send(TOPIC, item.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryEvent for item {}: {}",
                                item.getId(), ex.getMessage());
                    } else {
                        log.debug("Published InventoryEvent [INVENTORY_UPDATED] for item {}", item.getId());
                    }
                });
    }
}
