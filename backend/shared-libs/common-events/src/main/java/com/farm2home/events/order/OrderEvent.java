package com.farm2home.events.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Published to {@code order.events} whenever an order's lifecycle state changes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {

    /** ORDER_CREATED */
    private String eventType;
    private UUID orderId;
    private String orderNumber;
    private UUID customerId;
    private UUID subscriptionId;
    private LocalDate orderDate;
    private String orderType;
    private BigDecimal totalAmount;
    private List<OrderItem> items;
    private LocalDateTime occurredAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItem {
        private String milkType;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}
