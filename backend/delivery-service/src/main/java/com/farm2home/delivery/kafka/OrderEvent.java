package com.farm2home.delivery.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String eventType;
    private UUID orderId;
    private UUID customerId;
    private UUID subscriptionId;
    private String orderType;
    private String orderNumber;
    private BigDecimal totalAmount;
    private LocalDate orderDate;
    private String status;
}
